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
package io.github.rejeb.dataform.language.refactoring.column.apply;

import com.intellij.psi.PsiElement;
import com.intellij.usageView.UsageInfo;
import io.github.rejeb.dataform.language.refactoring.column.plan.ColumnRenamePlan;
import io.github.rejeb.dataform.language.refactoring.column.usage.ColumnRenameEdit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * One reviewable place of a column rename.
 *
 * <p>The rename window shows one row per instance of this class, and the user may exclude any of
 * them. The edit is carried along so that what is written is exactly what was reviewed, and so that
 * an excluded row writes nothing.</p>
 */
public final class ColumnRenameUsageInfo extends UsageInfo {

    private final ColumnRenameEdit edit;

    private ColumnRenameUsageInfo(@NotNull PsiElement anchor, @NotNull ColumnRenameEdit edit) {
        super(anchor, edit.rangeInAnchor().getStartOffset(), edit.rangeInAnchor().getEndOffset(),
                edit.isNonCode());
        this.edit = edit;
    }

    /**
     * The usage of an edit, or {@code null} when the element it points at is gone.
     */
    public static @Nullable ColumnRenameUsageInfo of(@NotNull ColumnRenameEdit edit) {
        PsiElement anchor = edit.anchor().getElement();
        return anchor == null ? null : new ColumnRenameUsageInfo(anchor, edit);
    }

    /** One row per place of a plan, in the order the places were collected. */
    public static UsageInfo @NotNull [] allOf(@NotNull ColumnRenamePlan plan) {
        List<UsageInfo> usages = new ArrayList<>();
        for (ColumnRenameEdit edit : plan.edits()) {
            ColumnRenameUsageInfo usage = of(edit);
            if (usage != null) usages.add(usage);
        }
        return usages.toArray(UsageInfo.EMPTY_ARRAY);
    }

    /** The place this row writes. */
    public @NotNull ColumnRenameEdit edit() {
        return edit;
    }
}
