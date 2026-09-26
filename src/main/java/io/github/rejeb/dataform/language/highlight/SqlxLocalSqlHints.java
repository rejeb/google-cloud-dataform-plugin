/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.rejeb.dataform.language.highlight;

import com.intellij.lang.annotation.AnnotationBuilder;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.sql.psi.SqlCompositeElementTypes;
import com.intellij.sql.psi.SqlReferenceExpression;
import io.github.rejeb.dataform.language.diagnostics.sql.bigquery.BigQueryDiagnosticsService;
import io.github.rejeb.dataform.language.diagnostics.sql.bigquery.BigQueryError;
import io.github.rejeb.dataform.language.diagnostics.sql.bigquery.BigQueryErrorKind;
import io.github.rejeb.dataform.language.diagnostics.sql.fix.ApplySqlFixAction;
import io.github.rejeb.dataform.language.diagnostics.sql.hint.NameSuggester;
import io.github.rejeb.dataform.language.diagnostics.sql.hint.PsiSqlScope;
import io.github.rejeb.dataform.language.diagnostics.sql.hint.SqlErrorContext;
import io.github.rejeb.dataform.language.diagnostics.sql.hint.SqlFix;
import io.github.rejeb.dataform.language.diagnostics.sql.hint.SqlHint;
import io.github.rejeb.dataform.language.diagnostics.sql.hint.SqlHints;
import io.github.rejeb.dataform.language.diagnostics.sql.hint.SqlScope;
import io.github.rejeb.dataform.language.schema.sql.SqlPsiParts;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Adds a fix to the problems the SQL support finds in a SQLX file: the column a misspelled name
 * most likely meant, and the comma a syntax error most likely lacks or has too many. The problems
 * themselves stay those of the SQL support. Only a fix is added, and nothing is said where
 * BigQuery already reported the same place.
 *
 * <p>A reference is resolved last, and only when a column close to its name exists. The annotator
 * runs early in the highlighting pass and in parallel with others, and resolving there a field of a
 * variable a {@code pre_operations} block declares yields that field twice, which the platform
 * reports as a non-idempotent resolve; the SQL support resolves every reference later anyway.</p>
 */
final class SqlxLocalSqlHints {

    private static final int MAX_SUGGESTIONS = 3;

    private SqlxLocalSqlHints() {
    }

    record LocalHint(@NotNull PsiFile hostFile, @NotNull SqlHint hint) {
    }

    private record HostPlace(@NotNull PsiFile file, @NotNull TextRange range) {
    }

    static void annotateColumnSuggestion(@NotNull SqlReferenceExpression reference, @NotNull AnnotationHolder holder) {
        if (reference.getReferenceElementType() != SqlCompositeElementTypes.SQL_COLUMN_REFERENCE) return;
        if (DumbService.isDumb(reference.getProject())) return;
        String name = reference.getName();
        PsiElement identifier = reference.getIdentifier();
        if (name == null || name.isBlank() || identifier == null) return;
        if (SqlxQuerySources.isGenerated(reference) || !SqlxQuerySources.areKnown(reference)) return;
        String unquoted = SqlPsiParts.unquoted(name);
        List<String> suggestions = NameSuggester.closest(unquoted,
                candidates(reference, PsiSqlScope.of(reference)), MAX_SUGGESTIONS);
        if (suggestions.isEmpty() || isDeclaredAlias(reference, unquoted) || isResolved(reference)) return;
        HostPlace host = hostPlaceOf(identifier);
        if (host == null || isReportedByBigQuery(host)) return;
        AnnotationBuilder builder = holder.newAnnotation(HighlightSeverity.WEAK_WARNING,
                "Did you mean " + SqlHints.quoted(suggestions) + "?").range(identifier);
        for (String suggestion : suggestions) {
            builder = builder.withFix(new ApplySqlFixAction(host.file(),
                    new SqlFix("Replace with '" + suggestion + "'", host.range(), suggestion)));
        }
        builder.create();
    }

    static @Nullable LocalHint syntaxHint(@NotNull PsiErrorElement error) {
        if (DumbService.isDumb(error.getProject())) return null;
        PsiElement token = error.getTextLength() > 0
                ? PsiTreeUtil.getDeepestFirst(error)
                : PsiTreeUtil.nextVisibleLeaf(error);
        if (token == null || token.getTextLength() == 0 || SqlxQuerySources.isGenerated(token)) return null;
        HostPlace host = hostPlaceOf(token);
        if (host == null || isReportedByBigQuery(host)) return null;
        BigQueryError local = new BigQueryError(BigQueryErrorKind.UNEXPECTED_TOKEN, error.getErrorDescription(),
                token.getText(), null, null, 0, 0);
        SqlHint hint = SqlHints.hintFor(new SqlErrorContext(local, host.range(), host.file().getText(), SqlScope.EMPTY));
        return hint.fixes().isEmpty() ? null : new LocalHint(host.file(), hint);
    }

    private static boolean isResolved(@NotNull SqlReferenceExpression reference) {
        PsiReference psiReference = reference.getReference();
        if (psiReference instanceof PsiPolyVariantReference poly) return poly.multiResolve(false).length > 0;
        return psiReference != null && psiReference.resolve() != null;
    }

    private static boolean isDeclaredAlias(@NotNull PsiElement reference, @NotNull String name) {
        PsiFile injected = reference.getContainingFile();
        Set<String> declared = CachedValuesManager.getCachedValue(injected, () -> CachedValueProvider.Result.create(
                declaredNames(injected), PsiModificationTracker.MODIFICATION_COUNT));
        return declared.contains(name.toLowerCase(Locale.ROOT));
    }

    private static @NotNull Set<String> declaredNames(@NotNull PsiFile injected) {
        Set<String> names = new HashSet<>();
        for (PsiElement alias : SqlPsiParts.childrenOfTypeDeep(injected, SqlCompositeElementTypes.SQL_AS_EXPRESSION)) {
            PsiElement identifier = SqlPsiParts.lastIdentifier(alias);
            if (identifier != null) names.add(SqlPsiParts.unquoted(identifier.getText()).toLowerCase(Locale.ROOT));
        }
        for (PsiElement definition : SqlPsiParts.childrenOfTypeDeep(injected,
                SqlCompositeElementTypes.SQL_NAMED_QUERY_DEFINITION)) {
            PsiElement identifier = SqlPsiParts.childOfType(definition, SqlCompositeElementTypes.SQL_IDENTIFIER);
            if (identifier != null) names.add(SqlPsiParts.unquoted(identifier.getText()).toLowerCase(Locale.ROOT));
        }
        return Set.copyOf(names);
    }

    private static @NotNull Collection<String> candidates(@NotNull SqlReferenceExpression reference,
                                                          @NotNull SqlScope scope) {
        PsiElement qualifier = reference.getQualifierExpression();
        if (qualifier == null) return scope.columns();
        if (!(qualifier instanceof SqlReferenceExpression source) || source.getQualifierExpression() != null) {
            return List.of();
        }
        String name = source.getName();
        if (name == null) return List.of();
        for (Map.Entry<String, List<String>> entry : scope.columnsBySource().entrySet()) {
            if (entry.getKey().equalsIgnoreCase(SqlPsiParts.unquoted(name))) return entry.getValue();
        }
        return List.of();
    }

    private static @Nullable HostPlace hostPlaceOf(@NotNull PsiElement injected) {
        InjectedLanguageManager manager = InjectedLanguageManager.getInstance(injected.getProject());
        PsiFile injectedFile = injected.getContainingFile();
        PsiFile hostFile = manager.getTopLevelFile(injectedFile);
        if (hostFile == null || hostFile == injectedFile) return null;
        return new HostPlace(hostFile, manager.injectedToHost(injected, injected.getTextRange()));
    }

    private static boolean isReportedByBigQuery(@NotNull HostPlace host) {
        return BigQueryDiagnosticsService.getInstance(host.file().getProject()).diagnose(host.file()).located()
                .stream().anyMatch(problem -> problem.range().intersects(host.range()));
    }
}
