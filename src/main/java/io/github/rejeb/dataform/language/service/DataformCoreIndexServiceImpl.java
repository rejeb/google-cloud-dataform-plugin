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
package io.github.rejeb.dataform.language.service;

import com.intellij.lang.javascript.psi.JSFunction;
import com.intellij.lang.javascript.psi.JSVariable;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptModule;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import io.github.rejeb.dataform.language.setup.DataformInterpreterManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Default {@link DataformCoreIndexService}. The symbols of {@code @dataform/core} are read from
 * its {@code bundle.d.ts} once and kept in memory for as long as that file is unchanged; they are
 * PSI elements, so they are never persisted and are rebuilt as soon as one of them stops being
 * valid.
 */
public final class DataformCoreIndexServiceImpl implements DataformCoreIndexService {

    private record Snapshot(@Nullable VirtualFile coreFile,
                            long stamp,
                            @NotNull Optional<PsiFile> dataformCoreJsFile,
                            @NotNull Collection<JSFunction> functions,
                            @NotNull Collection<JSVariable> variables,
                            @NotNull Collection<DataformFunctionCompletionObject> completions) {

        boolean isCurrent(@Nullable VirtualFile currentFile) {
            if (coreFile == null || currentFile == null) {
                return coreFile == currentFile;
            }
            return coreFile.equals(currentFile)
                    && stamp == currentFile.getModificationStamp()
                    && dataformCoreJsFile.map(PsiFile::isValid).orElse(true)
                    && functions.stream().allMatch(PsiElement::isValid)
                    && variables.stream().allMatch(PsiElement::isValid);
        }
    }

    private static final Snapshot EMPTY = new Snapshot(null, -1, Optional.empty(),
            Collections.emptyList(), Collections.emptyList(), Collections.emptyList());

    private final Project project;
    private volatile Snapshot snapshot = EMPTY;

    public DataformCoreIndexServiceImpl(Project project) {
        this.project = project;
    }

    @Override
    public Optional<PsiFile> getPsiFile() {
        return current().dataformCoreJsFile();
    }

    @Override
    @NotNull
    public Collection<JSFunction> getCachedDataformFunctionsRef() {
        return current().functions();
    }

    @Override
    @NotNull
    public Collection<JSVariable> getCachedDataformVariablesRef() {
        return current().variables();
    }

    @Override
    @NotNull
    public Collection<DataformFunctionCompletionObject> getCachedDataformFunctionsForCompletion() {
        return current().completions();
    }

    @NotNull
    private Snapshot current() {
        VirtualFile coreFile = findCoreDeclarationFile();
        Snapshot cached = snapshot;
        if (cached.isCurrent(coreFile)) {
            return cached;
        }
        Snapshot rebuilt = build(coreFile);
        snapshot = rebuilt;
        return rebuilt;
    }

    @Nullable
    private VirtualFile findCoreDeclarationFile() {
        return project.getService(DataformInterpreterManager.class)
                .dataformCorePath()
                .map(coreDir -> coreDir.findChild("bundle.d.ts"))
                .orElse(null);
    }

    @NotNull
    private Snapshot build(@Nullable VirtualFile coreFile) {
        if (coreFile == null) {
            return EMPTY;
        }
        Optional<PsiFile> psiFile = Optional.ofNullable(PsiManager.getInstance(project).findFile(coreFile));
        Collection<JSFunction> functions = findNonModuleElements(psiFile, JSFunction.class);
        Collection<JSVariable> variables = findNonModuleElements(psiFile, JSVariable.class);
        List<DataformFunctionCompletionObject> completions = functions.stream()
                .map(DataformFunctionCompletionObject::fromJSFunction)
                .flatMap(Optional::stream)
                .toList();
        return new Snapshot(coreFile, coreFile.getModificationStamp(), psiFile, functions, variables,
                completions);
    }

    private <T extends PsiElement> Collection<T> findNonModuleElements(
            Optional<PsiFile> dataformCoreJsFile,
            @NotNull Class<T> aClass) {
        return dataformCoreJsFile
                .map(tsFile -> PsiTreeUtil.findChildrenOfType(tsFile, aClass)
                        .stream()
                        .filter(elm -> PsiTreeUtil.getParentOfType(elm, TypeScriptModule.class) == null)
                        .toList())
                .orElse(Collections.emptyList());
    }
}
