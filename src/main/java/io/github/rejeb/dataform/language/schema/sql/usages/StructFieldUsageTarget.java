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

import com.intellij.navigation.ItemPresentation;
import com.intellij.pom.Navigatable;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.UsageInfo2UsageAdapter;
import com.intellij.usages.UsageSearcher;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.UsageViewManager;
import com.intellij.usages.UsageViewPresentation;
import io.github.rejeb.dataform.language.schema.sql.ColumnOriginService;
import io.github.rejeb.dataform.language.schema.sql.model.StructColumnPath;
import io.github.rejeb.dataform.language.DataformIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;

/**
 * What Find Usages searches for when the caret sits on a field of a struct column.
 *
 * <p>The platform cannot be asked for this the usual way. Find Usages starts from whatever the
 * reference under the caret resolves to, and a field resolves to an element of its column's type —
 * an element every struct of the same shape shares, in a file no project holds. Claiming it would
 * claim every one of them. A target of our own is offered instead, computed from the caret rather
 * than from a resolve, so the Find window searches the field the reader is actually pointing at.</p>
 *
 * <p>The usages come from the same search the column window uses, so the window and the Find window
 * cannot disagree.</p>
 */
final class StructFieldUsageTarget implements UsageTarget {

    private final Project project;
    private final StructColumnPath path;

    StructFieldUsageTarget(@NotNull Project project, @NotNull StructColumnPath path) {
        this.project = project;
        this.path = path;
    }

    @Override
    public void findUsages() {
        UsageViewPresentation presentation = new UsageViewPresentation();
        presentation.setTabText(path.leafName());
        presentation.setTargetsNodeText("Dataform column field");
        presentation.setCodeUsagesString("Reads of " + path.dottedName());
        presentation.setScopeText("project files");

        UsageViewManager.getInstance(project).searchAndShowUsages(
                new UsageTarget[]{this}, () -> searcher(), true, true, presentation, null);
    }

    /**
     * Reports every read of the field as a usage. Runs on the thread the usage view searches on, so
     * the PSI it reads is entered under a read action of its own. The Find window is where a list
     * too long for a popup goes, so the search it runs is not bounded.
     */
    private @NotNull UsageSearcher searcher() {
        return consumer -> new StructFieldColumnUsageSearch(project, path)
                .forEachRead(element -> ReadAction.nonBlocking(
                                () -> consumer.process(new UsageInfo2UsageAdapter(new UsageInfo(element))))
                        .executeSynchronously(), ColumnUsageRows.UNBOUNDED);
    }

    @Override
    public boolean isValid() {
        return path.root().isValid();
    }

    @Override
    public @NotNull String getName() {
        return path.dottedName();
    }

    @Override
    public @NotNull ItemPresentation getPresentation() {
        return new ItemPresentation() {
            @Override
            public @NotNull String getPresentableText() {
                return path.dottedName();
            }

            @Override
            public @Nullable String getLocationString() {
                return path.root().getTable() == null ? null : path.root().getTable().getName();
            }

            @Override
            public @Nullable Icon getIcon(boolean unused) {
                return DataformIcons.FILE;
            }
        };
    }

    /** Navigating the target lands on the field where it is written. */
    @Override
    public void navigate(boolean requestFocus) {
        PsiElement declaring = declaringElement();
        if (declaring instanceof Navigatable navigatable && navigatable.canNavigate()) {
            navigatable.navigate(requestFocus);
        }
    }

    @Override
    public boolean canNavigate() {
        return declaringElement() != null;
    }

    @Override
    public boolean canNavigateToSource() {
        return canNavigate();
    }

    private @Nullable PsiElement declaringElement() {
        return ColumnOriginService.getInstance(project).declaringElement(path);
    }
}
