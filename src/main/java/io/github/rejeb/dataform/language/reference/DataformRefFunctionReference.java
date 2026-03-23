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
package io.github.rejeb.dataform.language.reference;

import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReferenceBase;
import io.github.rejeb.dataform.language.compilation.model.*;
import io.github.rejeb.dataform.language.compilation.DataformCompilationService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class DataformRefFunctionReference extends PsiReferenceBase<PsiElement> {

    private final String tableName;

    public DataformRefFunctionReference(@NotNull PsiElement element,
                                        @NotNull String tableName,
                                        @NotNull TextRange range) {
        super(element, range);
        this.tableName = tableName;
    }

    @Override
    public @Nullable PsiElement resolve() {
        DataformCompilationService service = myElement.getProject()
                .getService(DataformCompilationService.class);
        CompiledGraph graph = service.getCompiledGraph();

        if (graph == null) {
            return null;
        }

        Optional<CompiledTable> table = graph.findTableByName(tableName);
        if (table.isPresent() && table.get().getFileName() != null) {
            return resolveFile(table.get().getFileName());
        }

        Optional<Declaration> declaration = graph.findDeclarationByName(tableName);

        if (declaration.isPresent() && declaration.get().getFileName() != null) {
            return resolveFile(declaration.get().getFileName());
        }

        Optional<CompiledAssertion> assertion = graph.findAssertionByName(tableName);
        if (assertion.isPresent() && assertion.get().getFileName() != null) {
            return resolveFile(assertion.get().getFileName());
        }

        Optional<CompiledOperation> operation = graph.findOperationByName(tableName);
        if (operation.isPresent() && operation.get().getFileName() != null) {
            return resolveFile(operation.get().getFileName());
        }
        return null;
    }

    private PsiElement resolveFile(String fileName) {
        VirtualFile projectRoot = ProjectUtil.guessProjectDir(myElement.getProject());
        if (projectRoot == null) {
            return null;
        }

        VirtualFile file = projectRoot.findFileByRelativePath(fileName.replace("\\", "/"));
        if (file != null) {
            return PsiManager.getInstance(myElement.getProject()).findFile(file);
        }

        return null;
    }

    @Override
    public Object @NotNull [] getVariants() {
        DataformCompilationService service = myElement.getProject()
                .getService(DataformCompilationService.class);
        CompiledGraph graph = service.getCompiledGraph();

        if (graph == null) {
            return EMPTY_ARRAY;
        }

        List<LookupElement> variants = new ArrayList<>();

        graph.getTables().stream()
                .filter(t -> t.getTarget() != null && !t.isDisabled())
                .map(table -> {
                    String name = table.getTarget().getName();
                    String fullName = table.getTarget().getFullName();

                    LookupElementBuilder element = LookupElementBuilder.create(name)
                            .withIcon(AllIcons.Nodes.DataTables)
                            .withTypeText(table.getType())
                            .withTailText(" (" + fullName + ")", true);
                    return PrioritizedLookupElement.withPriority(element, 200.0);
                })
                .forEach(variants::add);

        graph.getDeclarations().stream()
                .filter(d -> d.getTarget() != null)
                .map(declaration -> {
                    String name = declaration.getTarget().getName();
                    String fullName = declaration.getTarget().getFullName();

                    LookupElementBuilder element = LookupElementBuilder.create(name)
                            .withIcon(AllIcons.Nodes.DataSchema)
                            .withTypeText("source")
                            .withTailText(" (" + fullName + ")", true);
                    return PrioritizedLookupElement.withPriority(element, 200.0);
                })
                .forEach(variants::add);

        return variants.toArray();
    }
}

