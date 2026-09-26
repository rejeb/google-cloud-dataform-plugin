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
package io.github.rejeb.dataform.language.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TextWrapperTest {

    @Test
    public void shortTextStaysOnOneLine() {
        assertEquals(List.of("hello world"), TextWrapper.wrap("hello world", 20, ""));
    }

    @Test
    public void breaksOnWordBoundaries() {
        List<String> lines = TextWrapper.wrap("one two three four five", 9, "");
        assertEquals(List.of("one two", "three", "four five"), lines);
    }

    @Test
    public void prefixCountsTowardsTheFirstLine() {
        List<String> lines = TextWrapper.wrap("aaa bbb ccc", 8, "> ");
        assertEquals(List.of("> aaa", "bbb ccc"), lines);
    }

    @Test
    public void hardSplitsWordsLongerThanALine() {
        List<String> lines = TextWrapper.wrap("abcdefghijkl", 5, "");
        assertEquals(List.of("abcde", "fghij", "kl"), lines);
    }

    @Test
    public void longWordStartsANewLineBeforeBeingSplit() {
        List<String> lines = TextWrapper.wrap("ab cdefghijk", 6, "");
        assertEquals(List.of("ab", "cdefgh", "ijk"), lines);
    }

    @Test
    public void collapsesRunsOfWhitespace() {
        assertEquals(List.of("a b c"), TextWrapper.wrap("a   b\n\tc", 20, ""));
    }

    @Test
    public void blankTextProducesNoLine() {
        assertTrue(TextWrapper.wrap("   ", 20, "").isEmpty());
        assertTrue(TextWrapper.wrap("", 20, "").isEmpty());
    }

    @Test
    public void everyLineFitsTheWidth() {
        for (String line : TextWrapper.wrap("the quick brown fox jumps over the lazy dog", 7, "")) {
            assertTrue(line.length() <= 7, line);
        }
    }

    @Test
    public void keepingLineBreaksLeavesShortLinesUntouched() {
        assertEquals("a\n  b\n", TextWrapper.wrapKeepingLineBreaks("a  \n  b\n", 10));
    }

    @Test
    public void keepingLineBreaksWrapsLongLinesWithTheirIndent() {
        String wrapped = TextWrapper.wrapKeepingLineBreaks("  alpha beta gamma", 12);
        assertEquals("  alpha beta\ngamma", wrapped);
    }

    @Test
    public void keepingLineBreaksHandlesEmptyText() {
        assertEquals("", TextWrapper.wrapKeepingLineBreaks("", 10));
    }
}
