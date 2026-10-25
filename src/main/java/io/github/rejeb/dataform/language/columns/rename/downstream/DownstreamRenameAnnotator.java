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
package io.github.rejeb.dataform.language.columns.rename.downstream;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import io.github.rejeb.dataform.language.columns.impact.ColumnImpactService;
import io.github.rejeb.dataform.language.columns.model.ColumnRef;
import io.github.rejeb.dataform.language.columns.origin.ColumnOriginService;
import io.github.rejeb.dataform.language.columns.rename.usage.HostRanges;
import io.github.rejeb.dataform.language.compilation.DataformCompilationService;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.compilation.model.Target;
import io.github.rejeb.dataform.language.psi.SqlxFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Marks a column renamed in its action while downstream actions still read its old name, and
 * offers to rename those reads.
 */
public final class DownstreamRenameAnnotator implements Annotator {

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (!(element instanceof SqlxFile file)) return;
        VirtualFile virtualFile = file.getOriginalFile().getVirtualFile();
        CompiledGraph graph = DataformCompilationService.getInstance(file.getProject()).getCompiledGraph();
        if (virtualFile == null || graph == null) return;
        ColumnImpactService impacts = ColumnImpactService.getInstance(file.getProject());
        for (Target target : targetsOf(graph, virtualFile)) {
            for (DownstreamRename rename : impacts.pendingRenamesOf(target.getFullName())) {
                annotate(file, rename, holder);
            }
        }
    }

    private static @NotNull List<Target> targetsOf(@NotNull CompiledGraph graph, @NotNull VirtualFile file) {
        List<Target> targets = new ArrayList<>();
        graph.findTableByFileName(file.getPath()).forEach(table -> targets.add(table.getTarget()));
        graph.findOperationByFileName(file.getPath()).forEach(operation -> targets.add(operation.getTarget()));
        targets.removeIf(target -> target == null || target.getFullName() == null);
        return targets;
    }

    private static void annotate(@NotNull PsiFile file, @NotNull DownstreamRename rename,
                                 @NotNull AnnotationHolder holder) {
        PsiElement declaration = ColumnOriginService.getInstance(file.getProject())
                .declaringElement(new ColumnRef(rename.tableFullName(), rename.newName()));
        if (declaration == null || HostRanges.hostPsiFileOf(declaration) != file) return;
        TextRange range = HostRanges.hostRangeOf(declaration, TextRange.from(0, declaration.getTextLength()));
        if (range == null || range.isEmpty() || range.getEndOffset() > file.getTextLength()) return;
        holder.newAnnotation(HighlightSeverity.WARNING, rename.describe() + ": downstream actions still read `"
                        + rename.oldName() + "`")
                .range(range)
                .withFix(new DownstreamRenameFix(rename))
                .create();
    }
}
