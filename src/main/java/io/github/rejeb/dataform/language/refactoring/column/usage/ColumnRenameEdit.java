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
import com.intellij.psi.SmartPsiElementPointer;
import org.jetbrains.annotations.NotNull;

/**
 * One place the rename writes.
 *
 * <p>An edit carries both what to write and what to show: {@code hostRange} is the range of the host
 * document the processor replaces, while {@code anchor} and {@code rangeInAnchor} are the PSI the
 * rename window points a reviewable row at. Every edit must be reviewable, so both are required.</p>
 *
 * @param file          the host file the edit belongs to
 * @param hostRange     the range of the host document to replace
 * @param anchor        the element the review row points at
 * @param rangeInAnchor the range of the anchor holding the name
 * @param replacement   the text written in place of {@code hostRange}
 * @param kind          what the place is
 * @param risk          whether the place was found by resolution or by matching text
 * @param presentation  a short description for logs and the review window
 */
public record ColumnRenameEdit(@NotNull VirtualFile file,
                               @NotNull TextRange hostRange,
                               @NotNull SmartPsiElementPointer<PsiElement> anchor,
                               @NotNull TextRange rangeInAnchor,
                               @NotNull String replacement,
                               @NotNull Kind kind,
                               @NotNull Risk risk,
                               @NotNull String presentation) {

    /** What kind of place an edit sits in. */
    public enum Kind {
        SQL_DECLARATION,
        SQL_DECLARATION_ALIAS,
        SQL_STRUCT_ALIAS,
        SQL_STAR_EXPANSION,
        SQL_REFERENCE,
        CONFIG_COLUMN_KEY,
        CONFIG_COLUMN_NAME,
        CONFIG_PARTITION_EXPRESSION,
        CONFIG_ROW_CONDITION,
        JS_STRING,
        JS_IDENTIFIER
    }

    /**
     * How the place was found. A {@code CERTAIN} place was reached by resolving a reference or by
     * walking the config PSI; a {@code HEURISTIC} one by matching the name inside text, which the
     * user has to review.
     */
    public enum Risk {
        CERTAIN,
        HEURISTIC
    }

    /**
     * Whether the place belongs to the group of the rename window that holds text rather than code.
     *
     * <p>Only a guess goes there. A string that is exactly the column name was not guessed — a
     * helper is being handed the column — and the rename window keeps such a place selected, which
     * is what a place the rename is sure of has to be.</p>
     */
    public boolean isNonCode() {
        return risk == Risk.HEURISTIC;
    }
}
