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
package io.github.rejeb.dataform.language.schema.sql.usages;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The reads of a column of a table, found by the reference search Find Usages already runs, so a
 * row of the window and a row of the Find window come from the same place.
 *
 * <p>The search is allowed to process in parallel, which spends the cores on what costs: the
 * injected SQL built for each file the column's name appears in, and the resolve of every reference
 * in it.</p>
 */
final class ReferenceColumnUsageSearch implements ColumnUsageSearch {

    private static final Logger LOG = Logger.getInstance(ReferenceColumnUsageSearch.class);

    private final Project project;
    private final ColumnWindowTarget target;

    ReferenceColumnUsageSearch(@NotNull Project project, @NotNull ColumnWindowTarget target) {
        this.project = project;
        this.target = target;
    }

    @Override
    public void forEachRead(@NotNull Processor<PsiElement> reads, int maxReads) {
        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
        for (PsiElement searched : target.searchTargets()) {
            AtomicInteger reported = new AtomicInteger();
            ReferencesSearch.SearchParameters parameters =
                    new ReferencesSearch.SearchParameters(searched, scope, false);
            LOG.info("DIAG column search: " + describe(searched, parameters));
            boolean completed = ReferencesSearch.search(parameters)
                    .allowParallelProcessing()
                    .forEach((PsiReference found) -> {
                        reported.incrementAndGet();
                        return reads.process(found.getElement());
                    });
            LOG.info("DIAG column search done: name=" + target.name() + " reported=" + reported
                    + " completed=" + completed);
            if (!completed) return;
        }
    }

    private @NotNull String describe(@NotNull PsiElement searched,
                                     @NotNull ReferencesSearch.SearchParameters parameters) {
        StringBuilder out = new StringBuilder();
        out.append("name=").append(target.name());
        out.append(" target=").append(searched.getClass().getSimpleName());
        out.append(" valid=").append(searched.isValid());
        PsiFile file = searched.getContainingFile();
        VirtualFile virtualFile = file == null ? null : file.getVirtualFile();
        out.append(" file=").append(file == null ? "null" : file.getName());
        out.append(" vfile=").append(virtualFile == null ? "null" : virtualFile.getPath());
        if (virtualFile != null) {
            ProjectFileIndex index = ProjectFileIndex.getInstance(project);
            out.append(" inContent=").append(index.isInContent(virtualFile));
            out.append(" module=").append(ModuleUtilCore.findModuleForFile(virtualFile, project));
        }
        out.append(" dumb=").append(DumbService.isDumb(project));
        out.append(" useScope=").append(searched.getUseScope());
        out.append(" effectiveScope=").append(parameters.getEffectiveSearchScope());
        List<String> withWord = new ArrayList<>();
        PsiSearchHelper.getInstance(project).processAllFilesWithWord(target.name(),
                GlobalSearchScope.projectScope(project), candidate -> {
                    withWord.add(candidate.getName());
                    return true;
                }, true);
        out.append(" filesWithWord=").append(withWord);
        return out.toString();
    }
}
