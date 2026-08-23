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
import com.intellij.openapi.vfs.VirtualFile;
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

    /** The host file an element belongs to, or {@code null} when it has none on disk. */
    public static @Nullable VirtualFile hostFileOf(@NotNull PsiElement element) {
        PsiFile containing = element.getContainingFile();
        if (containing == null) return null;
        PsiFile host = InjectedLanguageManager.getInstance(element.getProject())
                .getTopLevelFile(containing);
        return host == null ? null : host.getVirtualFile();
    }

    /** The host file of an element, or {@code null} when it has none on disk. */
    public static @Nullable PsiFile hostPsiFileOf(@NotNull PsiElement element) {
        PsiFile containing = element.getContainingFile();
        return containing == null
                ? null
                : InjectedLanguageManager.getInstance(element.getProject()).getTopLevelFile(containing);
    }

    /**
     * The range of the host document holding {@code rangeInElement} of {@code element}.
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
        return manager.injectedToHost(element, absolute);
    }

    /** The whole range of an element, in the host document. */
    public static @Nullable TextRange hostRangeOf(@NotNull PsiElement element) {
        TextRange range = element.getTextRange();
        return range == null ? null : hostRangeOf(element, TextRange.from(0, range.getLength()));
    }
}
