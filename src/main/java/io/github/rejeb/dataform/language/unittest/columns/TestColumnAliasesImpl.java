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
package io.github.rejeb.dataform.language.unittest.columns;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import io.github.rejeb.dataform.language.compilation.DataformCompilationService;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.compilation.model.CompiledTest;
import io.github.rejeb.dataform.language.injection.InjectedFiles;
import io.github.rejeb.dataform.language.lineage.column.ColumnRef;
import io.github.rejeb.dataform.language.psi.SqlxSqlBlock;
import io.github.rejeb.dataform.language.refactoring.column.usage.HostRanges;
import io.github.rejeb.dataform.language.unittest.schema.TestBlockSchema;
import io.github.rejeb.dataform.language.unittest.schema.TestSchemaResolver;
import io.github.rejeb.dataform.language.util.DataformPaths;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class TestColumnAliasesImpl implements TestColumnAliases {

    private final Project project;

    public TestColumnAliasesImpl(@NotNull Project project) {
        this.project = project;
    }

    @Override
    public @NotNull Optional<TestColumnAlias> at(@NotNull PsiElement position) {
        return TestSchemaResolver.getInstance(project).blockAt(position)
                .flatMap(block -> in(block).stream()
                        .filter(alias -> PsiTreeUtil.isAncestor(alias.identifier(), position, false))
                        .findFirst());
    }

    @Override
    public @NotNull List<TestColumnAlias> in(@NotNull SqlxSqlBlock block) {
        Optional<TestBlockSchema> schema = TestSchemaResolver.getInstance(project).resolve(block);
        if (schema.isEmpty()) {
            return List.of();
        }
        String table = schema.get().target().getFullName();
        List<TestColumnAlias> aliases = new ArrayList<>();
        for (PsiFile injected : InjectedFiles.of(List.of(block))) {
            for (PsiElement expression : PsiTreeUtil.collectElements(injected,
                    element -> TestAliasPaths.isType(element, TestAliasPaths.AS_EXPRESSION))) {
                PsiElement identifier = TestAliasPaths.aliasIdentifier(expression);
                Optional<List<String>> path = TestAliasPaths.pathOf(expression);
                if (identifier == null || path.isEmpty() || !isWritten(identifier)) {
                    continue;
                }
                aliases.add(new TestColumnAlias(new ColumnRef(table, String.join(".", path.get())),
                        identifier, schema.get().kind()));
            }
        }
        return aliases;
    }

    @Override
    public @NotNull List<TestColumnAlias> of(@NotNull Set<ColumnRef> columns) {
        CompiledGraph graph = DataformCompilationService.getInstance(project).getCompiledGraph();
        if (graph == null || columns.isEmpty()) {
            return List.of();
        }
        Set<String> wanted = columns.stream().map(TestColumnAliasesImpl::key).collect(Collectors.toSet());
        List<TestColumnAlias> found = new ArrayList<>();
        for (CompiledTest test : graph.getTests()) {
            VirtualFile file = DataformPaths.findInProject(project, test.getFileName());
            PsiFile psi = file == null ? null : PsiManager.getInstance(project).findFile(file);
            if (psi == null) {
                continue;
            }
            for (SqlxSqlBlock block : PsiTreeUtil.findChildrenOfType(psi, SqlxSqlBlock.class)) {
                for (TestColumnAlias alias : in(block)) {
                    if (wanted.contains(key(alias.column()))) {
                        found.add(alias);
                    }
                }
            }
        }
        return found;
    }

    private static String key(@NotNull ColumnRef column) {
        return (column.tableFullName() + "#" + column.columnName()).toLowerCase(Locale.ROOT);
    }

    private static boolean isWritten(@NotNull PsiElement identifier) {
        return HostRanges.hostRangeOf(identifier, TextRange.from(0, identifier.getTextLength())) != null;
    }
}
