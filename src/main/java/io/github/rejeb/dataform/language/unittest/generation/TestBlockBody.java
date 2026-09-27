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
package io.github.rejeb.dataform.language.unittest.generation;

import com.intellij.openapi.util.TextRange;
import io.github.rejeb.dataform.language.unittest.schema.TestBlockKind;
import org.jetbrains.annotations.NotNull;

public final class TestBlockBody {

    private static final String INPUT_INDENT = "  ";

    private TestBlockBody() {
    }

    /**
     * Where and what to write so that a block holds the given {@code SELECT}. An input body is
     * replaced whole and indented under its braces; the expected query keeps the blank lines and
     * line comments leading it.
     */
    @NotNull
    public static Replacement replacement(@NotNull String blockText, @NotNull TestBlockKind kind, @NotNull String select) {
        if (kind == TestBlockKind.INPUT) {
            return new Replacement(new TextRange(0, blockText.length()),
                    "\n" + INPUT_INDENT + select + "\n");
        }
        int start = leadingTriviaEnd(blockText);
        return new Replacement(new TextRange(start, blockText.length()), select + "\n");
    }

    /**
     * Returns the indentation every line after the first of a generated {@code SELECT} needs.
     */
    @NotNull
    public static String indentOf(@NotNull TestBlockKind kind) {
        return kind == TestBlockKind.INPUT ? INPUT_INDENT : "";
    }

    private static int leadingTriviaEnd(@NotNull String text) {
        int offset = 0;
        while (offset < text.length()) {
            int lineEnd = text.indexOf('\n', offset);
            int end = lineEnd < 0 ? text.length() : lineEnd + 1;
            String line = text.substring(offset, end).trim();
            if (!line.isEmpty() && !line.startsWith("--") && !line.startsWith("#")) {
                return offset;
            }
            offset = end;
        }
        return text.length();
    }

    public record Replacement(@NotNull TextRange range, @NotNull String text) {
    }
}
