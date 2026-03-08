package io.github.rejeb.dataform.language.injection;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.compilation.model.Target;
import io.github.rejeb.dataform.language.service.DataformCompilationService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Resolves Dataform template expressions found in SQL blocks to BigQuery SQL identifiers.
 *
 * <p>Supported patterns:
 * <ul>
 *   <li>{@code ${ref("tableName")}} or {@code ${ref('tableName')}} → lookup in CompiledGraph</li>
 *   <li>{@code ${self()}} → lookup by current file name (without extension)</li>
 * </ul>
 *
 * <p>Returns a backtick-quoted BigQuery identifier, e.g. {@code `project.dataset.table`}.
 * Returns {@code null} if the pattern is unrecognized or the graph is unavailable/unresolved.
 */
public final class SqlxRefSelfResolver {

    /**
     * Matches ${ref("name")} and ${ref('name')} — single string argument only.
     */
    private static final Pattern REF_PATTERN = Pattern.compile(
            "\\$\\{\\s*ref\\s*\\(\\s*['\"]([^'\"]+)['\"]\\s*\\)\\s*\\}"
    );

    /**
     * Matches ${self()}.
     */
    private static final Pattern SELF_PATTERN = Pattern.compile(
            "\\$\\{\\s*self\\s*\\(\\s*\\)\\s*\\}"
    );

    private SqlxRefSelfResolver() {
    }

    /**
     * Attempts to resolve a TEMPLATE_EXPRESSION_ELEMENT or JS_LITERAL_ELEMENT PSI element
     * to a BigQuery-qualified SQL identifier.
     *
     * @param element         PSI element whose text is e.g. {@code ${ref("my_table")}}
     * @param currentFileName name of the .sqlx file without extension, used for {@code ${self()}}
     * @return BigQuery identifier like {@code `project.dataset.table`}, or {@code null} if unresolvable
     */
    @Nullable
    public static String resolveToSqlIdentifier(@NotNull PsiElement element,
                                                @Nullable String currentFileName) {
        String text = element.getText();
        if (text == null || text.isBlank()) return null;

        Project project = element.getProject();
        DataformCompilationService compilationService =
                project.getService(DataformCompilationService.class);
        if (compilationService == null) return null;

        CompiledGraph graph = compilationService.getCompiledGraph();
        if (graph == null) return null;

        // Case 1: ${ref("tableName")} or ${ref('tableName')}
        Matcher refMatcher = REF_PATTERN.matcher(text);
        if (refMatcher.matches()) {
            String refName = refMatcher.group(1);
            return graph.findTargetByRefName(refName)
                    .map(SqlxRefSelfResolver::toBigQueryIdentifier)
                    .orElse(null);
        }

        // Case 2: ${self()} — resolves to the current file's own action target
        if (SELF_PATTERN.matcher(text).matches() && currentFileName != null) {
            return graph.findTargetByRefName(currentFileName)
                    .map(SqlxRefSelfResolver::toBigQueryIdentifier)
                    .orElse(null);
        }

        return null;
    }


    @NotNull
    private static String toBigQueryIdentifier(@NotNull Target target) {
        return Stream.of(target.getDatabase(), target.getSchema(), target.getName())
                .filter(part -> part != null && !part.isBlank())
                .map(SqlxRefSelfResolver::quoteIfNeeded)
                .collect(Collectors.joining("."));
    }


    private static String quoteIfNeeded(@NotNull String part) {
        return part.matches("[a-zA-Z_][a-zA-Z0-9_]*") ? part : "`" + part + "`";
    }
}
