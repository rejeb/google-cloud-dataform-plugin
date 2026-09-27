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
package io.github.rejeb.dataform.language.unittest.schema;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import io.github.rejeb.dataform.language.compilation.DataformCompilationService;
import io.github.rejeb.dataform.language.compilation.model.ActionReference;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.compilation.model.Target;
import io.github.rejeb.dataform.language.psi.SharedTokenTypes;
import io.github.rejeb.dataform.language.psi.SqlxInputBlock;
import io.github.rejeb.dataform.language.psi.SqlxSqlBlock;
import io.github.rejeb.dataform.language.schema.sql.DataformTableSchemaService;
import io.github.rejeb.dataform.language.schema.sql.model.ColumnInfo;
import io.github.rejeb.dataform.language.schema.sql.model.DataformDasTable;
import io.github.rejeb.dataform.language.unittest.SqlxUnitTests;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

public final class TestSchemaResolverImpl implements TestSchemaResolver {

    private final Project project;

    public TestSchemaResolverImpl(@NotNull Project project) {
        this.project = project;
    }

    @Override
    public @NotNull Optional<SqlxSqlBlock> blockAt(@NotNull PsiElement element) {
        PsiLanguageInjectionHost host = InjectedLanguageManager.getInstance(project).getInjectionHost(element);
        PsiElement start = host != null ? host : element;
        SqlxSqlBlock block = PsiTreeUtil.getParentOfType(start, SqlxSqlBlock.class, false);
        if (block != null) {
            return Optional.of(block);
        }
        SqlxInputBlock input = PsiTreeUtil.getParentOfType(start, SqlxInputBlock.class, false);
        return input == null ? Optional.empty() : Optional.ofNullable(input.content());
    }

    @Override
    public @NotNull Optional<TestBlockSchema> resolve(@NotNull SqlxSqlBlock block) {
        PsiFile file = block.getContainingFile();
        if (file == null || !SqlxUnitTests.isUnitTestFile(file)) {
            return Optional.empty();
        }
        CompiledGraph graph = DataformCompilationService.getInstance(project).getCompiledGraph();
        if (graph == null) {
            return Optional.empty();
        }
        Optional<TestBlockKind> kind = kindOf(block);
        if (kind.isEmpty()) {
            return Optional.empty();
        }
        return referenceOf(kind.get(), block, file)
                .flatMap(graph::findTargetByReference)
                .flatMap(target -> {
                    List<ColumnInfo> columns = columnsOf(target);
                    return columns.isEmpty()
                            ? Optional.empty()
                            : Optional.of(new TestBlockSchema(kind.get(), target, columns));
                });
    }

    @Override
    public @NotNull Optional<TestBlockSchema> resolveAt(@NotNull PsiElement element) {
        return blockAt(element).flatMap(this::resolve);
    }

    @Override
    public @NotNull List<ColumnInfo> columnsOf(@Nullable Target target) {
        if (target == null || target.getFullName() == null) {
            return List.of();
        }
        DataformDasTable table = DataformTableSchemaService.getInstance(project).getAllTables()
                .get(target.getFullName());
        return table == null ? List.of() : table.getColumns();
    }

    private static Optional<TestBlockKind> kindOf(@NotNull SqlxSqlBlock block) {
        IElementType type = block.getNode().getElementType();
        if (type == SharedTokenTypes.INPUT_CONTENT && block.getParent() instanceof SqlxInputBlock) {
            return Optional.of(TestBlockKind.INPUT);
        }
        if (type == SharedTokenTypes.SQL_CONTENT) {
            return Optional.of(TestBlockKind.EXPECTED);
        }
        return Optional.empty();
    }

    private static Optional<ActionReference> referenceOf(@NotNull TestBlockKind kind,
                                                         @NotNull SqlxSqlBlock block,
                                                         @NotNull PsiFile file) {
        if (kind == TestBlockKind.INPUT) {
            return inputReference(((SqlxInputBlock) block.getParent()).labelParts());
        }
        return SqlxUnitTests.testedDatasetName(file).map(ActionReference::named);
    }

    private static Optional<ActionReference> inputReference(@NotNull List<String> parts) {
        return switch (parts.size()) {
            case 1 -> Optional.of(new ActionReference(null, null, parts.get(0)));
            case 2 -> Optional.of(new ActionReference(null, parts.get(0), parts.get(1)));
            case 3 -> Optional.of(new ActionReference(parts.get(0), parts.get(1), parts.get(2)));
            default -> Optional.empty();
        };
    }
}
