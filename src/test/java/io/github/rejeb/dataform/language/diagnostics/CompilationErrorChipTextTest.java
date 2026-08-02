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

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CompilationErrorChipTextTest {

    @Test
    public void shortMessageStaysOnOneLineWithPrefix() {
        assertEquals(List.of("⚠ boom"), CompilationErrorChipText.wrap("boom"));
    }

    @Test
    public void messageUnderEightyCharactersIsNotWrapped() {
        String message = "x".repeat(70);
        assertEquals(1, CompilationErrorChipText.wrap(message).size());
    }

    @Test
    public void messageOverEightyCharactersWrapsOntoMultipleLines() {
        String message = "Could not resolve the referenced table because the declaration "
                + "is missing from the project configuration file";
        List<String> lines = CompilationErrorChipText.wrap(message);
        assertTrue(lines.size() > 1, "expected wrapping, got " + lines);
        for (String line : lines) {
            assertTrue(line.length() <= CompilationErrorChipText.MAX_LINE_LENGTH,
                    "line too long: " + line.length() + " -> " + line);
        }
    }

    @Test
    public void wrappingBreaksOnWordBoundaries() {
        String message = "alpha beta gamma delta epsilon zeta eta theta iota kappa lambda mu "
                + "nu xi omicron pi rho sigma tau upsilon";
        for (String line : CompilationErrorChipText.wrap(message)) {
            assertTrue(line.length() <= CompilationErrorChipText.MAX_LINE_LENGTH);
        }
        String joined = String.join(" ", CompilationErrorChipText.wrap(message));
        assertTrue(joined.contains("omicron"), "words must not be split: " + joined);
    }

    @Test
    public void veryLongSingleWordIsHardSplit() {
        String message = "y".repeat(200);
        List<String> lines = CompilationErrorChipText.wrap(message);
        assertTrue(lines.size() >= 3, "expected hard split, got " + lines.size());
        for (String line : lines) {
            assertTrue(line.length() <= CompilationErrorChipText.MAX_LINE_LENGTH);
        }
    }

    @Test
    public void newlinesInMessageAreCollapsed() {
        assertEquals(List.of("⚠ first second"),
                CompilationErrorChipText.wrap("first\n   second"));
    }

    @Test
    public void blankAndNullMessagesFallBack() {
        assertEquals(List.of("⚠ Compilation error"), CompilationErrorChipText.wrap("   "));
        assertEquals(List.of("⚠ Compilation error"), CompilationErrorChipText.wrap(null));
    }
}
