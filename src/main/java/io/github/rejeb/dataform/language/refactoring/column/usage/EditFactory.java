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
package io.github.rejeb.dataform.language.refactoring.column.usage;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Builds an edit from a place in the PSI, doing the host mapping and the pointer creation once.
 */
public final class EditFactory {

    private EditFactory() {
    }

    /**
     * An edit replacing {@code rangeInAnchor} of {@code anchor}, or {@code null} when the element
     * has no host document to write.
     */
    public static @Nullable ColumnRenameEdit of(@NotNull PsiElement anchor,
                                                @NotNull TextRange rangeInAnchor,
                                                @NotNull String replacement,
                                                @NotNull ColumnRenameEdit.Kind kind,
                                                @NotNull ColumnRenameEdit.Risk risk,
                                                @NotNull String presentation) {
        VirtualFile file = HostRanges.hostFileOf(anchor);
        TextRange hostRange = HostRanges.hostRangeOf(anchor, rangeInAnchor);
        if (file == null || hostRange == null) return null;
        return new ColumnRenameEdit(file, hostRange,
                SmartPointerManager.getInstance(anchor.getProject())
                        .createSmartPsiElementPointer(anchor),
                rangeInAnchor, replacement, kind, risk, presentation);
    }

    /** An edit replacing the whole of {@code anchor}. */
    public static @Nullable ColumnRenameEdit ofWhole(@NotNull PsiElement anchor,
                                                     @NotNull String replacement,
                                                     @NotNull ColumnRenameEdit.Kind kind,
                                                     @NotNull ColumnRenameEdit.Risk risk,
                                                     @NotNull String presentation) {
        TextRange range = anchor.getTextRange();
        if (range == null) return null;
        return of(anchor, TextRange.from(0, range.getLength()), replacement, kind, risk, presentation);
    }
}
