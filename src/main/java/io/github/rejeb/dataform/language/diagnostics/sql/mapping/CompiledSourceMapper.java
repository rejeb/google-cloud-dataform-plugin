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
package io.github.rejeb.dataform.language.diagnostics.sql.mapping;

import com.intellij.diff.comparison.ComparisonManager;
import com.intellij.diff.comparison.ComparisonPolicy;
import com.intellij.diff.comparison.DiffTooBigException;
import com.intellij.diff.fragments.DiffFragment;
import com.intellij.openapi.progress.DumbProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Traces a place of a compiled query to the SQLX file it was compiled from, as the file reads now.
 *
 * <p>The compiled text keeps the SQL of the file word for word, in the same order; what differs
 * is the value each {@code ${…}} hole was given, the blocks Dataform leaves out, and whatever the
 * user has typed since. The two texts are compared word by word, so a place in text they still
 * share is found exactly, a place in the value of a hole is the hole, and a place in text changed
 * since has nothing left to point at.</p>
 */
public final class CompiledSourceMapper {

    private CompiledSourceMapper() {
    }

    /**
     * Where a place of the compiled source lies in the SQLX file.
     *
     * @param offset the host offset, or -1 for {@link #UNKNOWN}
     * @param hole   the template hole whose value holds the place, or {@code null}
     */
    public record HostPlace(int offset, @Nullable TextRange hole) {

        /**
         * The place of a source too far from the file to be compared with it: whether the text
         * there changed since the compilation cannot be told.
         */
        public static final HostPlace UNKNOWN = new HostPlace(-1, null);
    }

    /**
     * The host place of an offset of a compiled source, {@code null} when the text there has
     * changed since the compilation, or {@link HostPlace#UNKNOWN} when the two texts are too far
     * apart to be compared. Windows line ends in the compiled text are read as plain ones.
     */
    public static @Nullable HostPlace toHost(@NotNull String compiled,
                                             int compiledOffset,
                                             @NotNull SqlxSourceText sqlx) {
        String normalized = StringUtil.convertLineSeparators(compiled);
        int offset = compiledOffset - removedCarriageReturns(compiled, compiledOffset);
        List<DiffFragment> fragments;
        try {
            fragments = ComparisonManager.getInstance().compareWords(
                    normalized, sqlx.text(), ComparisonPolicy.DEFAULT, indicator());
        } catch (DiffTooBigException e) {
            return HostPlace.UNKNOWN;
        }
        int delta = 0;
        for (DiffFragment fragment : fragments) {
            if (offset < fragment.getStartOffset1()) break;
            if (offset < fragment.getEndOffset1()) {
                TextRange changed = new TextRange(
                        sqlx.toHost(fragment.getStartOffset2()), sqlx.toHost(fragment.getEndOffset2()));
                TextRange hole = sqlx.holeTouching(changed);
                return hole == null ? null : new HostPlace(hole.getStartOffset(), hole);
            }
            delta = fragment.getEndOffset2() - fragment.getEndOffset1();
        }
        int hostOffset = sqlx.toHost(Math.min(offset + delta, sqlx.text().length()));
        return new HostPlace(hostOffset, sqlx.holeAt(hostOffset));
    }

    private static int removedCarriageReturns(@NotNull String text, int before) {
        int removed = 0;
        for (int i = 0; i < before && i < text.length(); i++) {
            if (text.charAt(i) == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') removed++;
        }
        return removed;
    }

    private static @NotNull ProgressIndicator indicator() {
        ProgressIndicator current = ProgressIndicatorProvider.getGlobalProgressIndicator();
        return current != null ? current : DumbProgressIndicator.INSTANCE;
    }
}
