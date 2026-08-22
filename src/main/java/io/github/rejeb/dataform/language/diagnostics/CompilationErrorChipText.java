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
package io.github.rejeb.dataform.language.diagnostics;

import io.github.rejeb.dataform.language.util.TextWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Lays out a compilation error message into the lines rendered inside an editor chip.
 */
public final class CompilationErrorChipText {

    public static final int MAX_LINE_LENGTH = 80;

    private static final String PREFIX = "⚠ ";
    private static final String FALLBACK = "Compilation error";

    private CompilationErrorChipText() {
    }

    /**
     * Splits the message into chip lines of at most {@link #MAX_LINE_LENGTH} characters, breaking
     * on word boundaries where possible. The first line carries the warning prefix.
     */
    public static @NotNull List<String> wrap(@Nullable String message) {
        List<String> lines = TextWrapper.wrap(normalize(message), MAX_LINE_LENGTH, PREFIX);
        return lines.isEmpty() ? List.of(PREFIX + FALLBACK) : lines;
    }

    private static String normalize(@Nullable String message) {
        if (message == null || message.isBlank()) {
            return FALLBACK;
        }
        String collapsed = message.replaceAll("\\s+", " ").trim();
        return collapsed.isEmpty() ? FALLBACK : collapsed;
    }
}
