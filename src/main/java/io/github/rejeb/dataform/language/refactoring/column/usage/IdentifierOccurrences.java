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
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds a name inside a piece of text, as a whole identifier.
 *
 * <p>Used wherever a column name is written in text rather than in code: a partitioning expression,
 * a row condition, a JavaScript string. A match is only reported when the characters around it
 * cannot be part of an identifier, so {@code order_id} never matches inside {@code order_id_2}.</p>
 */
public final class IdentifierOccurrences {

    private IdentifierOccurrences() {
    }

    /** The ranges of {@code text} holding {@code name} as a whole identifier. */
    public static @NotNull List<TextRange> of(@NotNull String text, @NotNull String name) {
        List<TextRange> ranges = new ArrayList<>();
        if (name.isEmpty()) return ranges;
        int from = 0;
        while (true) {
            int at = text.indexOf(name, from);
            if (at < 0) return ranges;
            int end = at + name.length();
            if (isBoundary(text, at - 1) && isBoundary(text, end)) {
                ranges.add(new TextRange(at, end));
            }
            from = end;
        }
    }

    private static boolean isBoundary(@NotNull String text, int index) {
        if (index < 0 || index >= text.length()) return true;
        char character = text.charAt(index);
        return !Character.isLetterOrDigit(character) && character != '_' && character != '$';
    }
}
