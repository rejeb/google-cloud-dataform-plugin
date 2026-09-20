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

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Maps a place found in the PSI to the host document that has to be written.
 *
 * <p>Most places of a rename live in an injected file — the SQL of a block, the JavaScript of a
 * config — whose document is a window over the SQLX file. The processor writes host documents, so
 * every range is translated once, here, at the moment it is collected.</p>
 */
public final class HostRanges {

    private HostRanges() {
    }

    /** The host file of an element, or {@code null} when it has none. */
    public static @Nullable PsiFile hostPsiFileOf(@NotNull PsiElement element) {
        PsiFile containing = element.getContainingFile();
        return containing == null
                ? null
                : InjectedLanguageManager.getInstance(element.getProject()).getTopLevelFile(containing);
    }

    /**
     * The range of the host document holding {@code rangeInElement} of {@code element}, or
     * {@code null} when the host holds no such text.
     *
     * <p>An injection writes text of its own around what the host holds — the value a template
     * hole stands for, for one. A range inside that text maps to nothing a rename could write:
     * the platform answers with the empty range at the hole, and an edit there would insert the
     * new name in front of the template instead of renaming anything.</p>
     *
     * @param element       the element the range belongs to
     * @param rangeInElement the range inside the element, relative to its own start
     */
    public static @Nullable TextRange hostRangeOf(@NotNull PsiElement element,
                                                  @NotNull TextRange rangeInElement) {
        TextRange elementRange = element.getTextRange();
        if (elementRange == null) return null;
        TextRange absolute = rangeInElement.shiftRight(elementRange.getStartOffset());
        InjectedLanguageManager manager = InjectedLanguageManager.getInstance(element.getProject());
        PsiFile containing = element.getContainingFile();
        if (containing == null) return null;
        if (!manager.isInjectedFragment(containing)) return absolute;
        if (!isEditable(manager, containing, absolute)) return null;
        return manager.injectedToHost(element, absolute);
    }

    private static boolean isEditable(@NotNull InjectedLanguageManager manager,
                                      @NotNull PsiFile injected, @NotNull TextRange range) {
        for (TextRange editable : manager.intersectWithAllEditableFragments(injected, range)) {
            if (editable.getLength() == range.getLength()) return true;
        }
        return false;
    }
}
