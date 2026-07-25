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
package io.github.rejeb.dataform.language.folding;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;

/**
 * Decides which expressions are rendered as a multi-line value instead of a one-line placeholder.
 *
 * <p>A multi-line rendering uses a custom fold region, which can only span whole document lines, so
 * the expression must be alone on its lines. Both the folding builders and the fold manager consult
 * this policy, so they always agree on which regions each owns.</p>
 */
public final class DataformMultilineFoldPolicy {

    private static final int MAX_ONE_LINE_LENGTH = 100;

    private DataformMultilineFoldPolicy() {
    }

    /**
     * Tells whether the value of the expression at the given range should be painted over several lines.
     *
     * <p>The custom fold region covers whole lines, and the renderer repaints whatever code shares
     * them, so an expression does not need to be alone on its lines.</p>
     */
    public static boolean qualifies(@NotNull Document document, @NotNull TextRange range, @NotNull String value) {
        return isWorthMultipleLines(value) && isWithinDocument(document, range);
    }

    /**
     * Returns the text preceding the expression on its first line.
     */
    @NotNull
    public static String prefixOf(@NotNull Document document, @NotNull TextRange range) {
        int lineStart = document.getLineStartOffset(startLine(document, range));
        return document.getCharsSequence().subSequence(lineStart, range.getStartOffset()).toString();
    }

    /**
     * Returns the text following the expression on its last line.
     */
    @NotNull
    public static String suffixOf(@NotNull Document document, @NotNull TextRange range) {
        int lineEnd = document.getLineEndOffset(endLine(document, range));
        return document.getCharsSequence().subSequence(range.getEndOffset(), lineEnd).toString();
    }

    private static boolean isWorthMultipleLines(@NotNull String value) {
        String expanded = DataformFoldingPlaceholder.expanded(value);
        return expanded.contains("\n") || expanded.length() > MAX_ONE_LINE_LENGTH;
    }

    private static boolean isWithinDocument(@NotNull Document document, @NotNull TextRange range) {
        return range.getStartOffset() >= 0 && range.getEndOffset() <= document.getTextLength();
    }

    /**
     * Returns the first document line of the range.
     */
    public static int startLine(@NotNull Document document, @NotNull TextRange range) {
        return document.getLineNumber(range.getStartOffset());
    }

    /**
     * Returns the last document line of the range.
     */
    public static int endLine(@NotNull Document document, @NotNull TextRange range) {
        return document.getLineNumber(range.getEndOffset());
    }
}
