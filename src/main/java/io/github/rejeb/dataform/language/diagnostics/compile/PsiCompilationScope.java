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
package io.github.rejeb.dataform.language.diagnostics.compile;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import io.github.rejeb.dataform.language.compilation.DataformCompilationService;
import io.github.rejeb.dataform.language.compilation.model.CompiledAssertion;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.compilation.model.CompiledOperation;
import io.github.rejeb.dataform.language.compilation.model.CompiledTable;
import io.github.rejeb.dataform.language.compilation.model.Declaration;
import io.github.rejeb.dataform.language.compilation.model.Target;
import io.github.rejeb.dataform.language.completion.config.ConfigSchemaLookup;
import io.github.rejeb.dataform.language.psi.SqlxConfigBlock;
import io.github.rejeb.dataform.language.psi.SqlxFile;
import io.github.rejeb.dataform.language.psi.SqlxJsBlock;
import io.github.rejeb.dataform.language.util.DataformProjectLayout;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The {@link CompilationScope} of a place of a SQLX or JavaScript file, read from its PSI, the
 * includes of its project and the compiled graph.
 */
public final class PsiCompilationScope implements CompilationScope {

    private static final Pattern DECLARATION =
            Pattern.compile("\\b(?:const|let|var|function|class)\\s+([A-Za-z_$][\\w$]*)");
    private static final List<String> DATAFORM_NAMES = List.of("ref", "resolve", "self", "name", "when",
            "incremental", "schema", "database", "dataform", "publish", "declare", "assert", "operate", "test");

    private final PsiFile file;
    private final int offset;

    private PsiCompilationScope(@NotNull PsiFile file, int offset) {
        this.file = file;
        this.offset = offset;
    }

    /**
     * The scope of a host offset of a SQLX or JavaScript file.
     */
    public static @NotNull CompilationScope at(@NotNull PsiFile file, int hostOffset) {
        return new PsiCompilationScope(file, hostOffset);
    }

    @Override
    public @NotNull Collection<String> jsNames() {
        Set<String> names = new LinkedHashSet<>();
        String text = file.getText();
        for (TextRange range : scriptRanges()) {
            Matcher matcher = DECLARATION.matcher(text).region(range.getStartOffset(), range.getEndOffset());
            while (matcher.find()) names.add(matcher.group(1));
        }
        names.addAll(DataformProjectLayout.includeNames(file.getOriginalFile().getVirtualFile()));
        names.addAll(DATAFORM_NAMES);
        return List.copyOf(names);
    }

    @Override
    public @NotNull Collection<String> actionNames() {
        CompiledGraph graph = DataformCompilationService.getInstance(file.getProject()).getCompiledGraph();
        if (graph == null) return List.of();
        Set<String> names = new LinkedHashSet<>();
        Stream.of(targets(graph.getTables(), CompiledTable::getTarget),
                        targets(graph.getOperations(), CompiledOperation::getTarget),
                        targets(graph.getDeclarations(), Declaration::getTarget),
                        targets(graph.getAssertions(), CompiledAssertion::getTarget))
                .flatMap(stream -> stream)
                .filter(target -> target != null && target.getName() != null)
                .map(Target::getName)
                .forEach(names::add);
        return List.copyOf(names);
    }

    @Override
    public @NotNull Collection<String> configKeys() {
        PsiElement injected = InjectedLanguageManager.getInstance(file.getProject()).findInjectedElementAt(file, offset);
        if (injected == null) return List.of();
        return ConfigSchemaLookup.create(injected)
                .flatMap(lookup -> lookup.enclosingObjectSchema(injected).map(lookup::properties))
                .map(properties -> List.copyOf(properties.keySet()))
                .orElse(List.of());
    }

    @Override
    public boolean isInConfig() {
        SqlxConfigBlock config = PsiTreeUtil.findChildOfType(file, SqlxConfigBlock.class);
        return config != null && config.getTextRange().contains(offset);
    }

    private @NotNull List<TextRange> scriptRanges() {
        if (!(file instanceof SqlxFile)) return List.of(file.getTextRange());
        if (isInConfig()) return List.of();
        return PsiTreeUtil.findChildrenOfType(file, SqlxJsBlock.class).stream().map(PsiElement::getTextRange).toList();
    }

    private static <A> @NotNull Stream<Target> targets(@Nullable List<A> actions, @NotNull Function<A, Target> target) {
        return actions == null ? Stream.empty() : actions.stream().map(target);
    }
}
