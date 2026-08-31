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

import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.SmartPsiFileRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * One place the rename writes.
 *
 * <p>A place is always a range of a host file, never of an injected one. The SQL, the JavaScript and
 * the config of a SQLX file are injected, and a pointer into an injected file has to rebuild the
 * injection to answer what file it belongs to, which the rename window asks for while it paints. So
 * every place is translated to its host once, where it is collected, and held as a range the
 * document keeps up to date.</p>
 *
 * @param file          the host file the edit belongs to
 * @param hostRange     the range of the host document to replace, as it stood when collected
 * @param place         the same range, kept up to date with what is typed while the window is open
 * @param replacement   the text written in place of the range
 * @param kind          what the place is
 * @param risk          whether the place was found by resolution or by matching text
 * @param presentation  a short description for logs and the review window
 */
public record ColumnRenameEdit(@NotNull VirtualFile file,
                               @NotNull TextRange hostRange,
                               @NotNull SmartPsiFileRange place,
                               @NotNull String replacement,
                               @NotNull Kind kind,
                               @NotNull Risk risk,
                               @NotNull String presentation) {

    /**
     * What identifies the place, so that two collectors reaching the same characters produce one
     * edit rather than two.
     */
    public @NotNull String key() {
        return file.getPath() + "@" + hostRange.getStartOffset() + "-" + hostRange.getEndOffset();
    }

    /**
     * The range the place occupies in its host document now, or {@code null} when what was collected
     * is gone. A plan is reviewed in the rename window, which the user may leave open while typing
     * somewhere else, so what a place covers today is asked for rather than assumed.
     */
    public @Nullable TextRange currentRange() {
        Segment range = place.getRange();
        if (range == null) return null;
        TextRange current = TextRange.create(range);
        return current.isEmpty() ? null : current;
    }

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
