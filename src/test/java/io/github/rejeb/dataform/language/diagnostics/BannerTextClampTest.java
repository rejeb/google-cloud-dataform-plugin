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

import java.util.function.ToIntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BannerTextClampTest {

    private static final ToIntFunction<String> ONE_PIXEL_PER_CHAR = String::length;

    @Test
    public void shortTextIsKeptAsIs() {
        BannerTextClamp.Clamped clamped = BannerTextClamp.clamp("short error", 2, 20, ONE_PIXEL_PER_CHAR);
        assertEquals("short error", clamped.text());
        assertFalse(clamped.truncated());
    }

    @Test
    public void textIsWrappedOnWordsWhenItFits() {
        BannerTextClamp.Clamped clamped = BannerTextClamp.clamp("aaa bbb ccc", 2, 7, ONE_PIXEL_PER_CHAR);
        assertEquals("aaa bbb\nccc", clamped.text());
        assertFalse(clamped.truncated());
    }

    @Test
    public void overflowingTextEndsWithAnEllipsis() {
        BannerTextClamp.Clamped clamped = BannerTextClamp.clamp("aaa bbb ccc ddd eee", 2, 7, ONE_PIXEL_PER_CHAR);
        assertEquals("aaa bbb\nccc...", clamped.text());
        assertTrue(clamped.truncated());
    }

    @Test
    public void everyVisibleLineFitsTheWidth() {
        String text = "Dataform:\n• first problem with a long description\n• second problem\n• third";
        BannerTextClamp.Clamped clamped = BannerTextClamp.clamp(text, 3, 12, ONE_PIXEL_PER_CHAR);
        assertTrue(clamped.truncated());
        String[] lines = clamped.text().split("\n");
        assertEquals(3, lines.length);
        for (String line : lines) {
            assertTrue(line.length() <= 12, line);
        }
        assertTrue(lines[2].endsWith(BannerTextClamp.ELLIPSIS));
    }

    @Test
    public void explicitLineBreaksCountAsLines() {
        BannerTextClamp.Clamped clamped = BannerTextClamp.clamp("one\ntwo\nthree", 2, 50, ONE_PIXEL_PER_CHAR);
        assertEquals("one\ntwo...", clamped.text());
        assertTrue(clamped.truncated());
    }

    @Test
    public void wordWiderThanTheBannerIsBrokenAcrossLines() {
        BannerTextClamp.Clamped clamped = BannerTextClamp.clamp("abcdefghij", 3, 4, ONE_PIXEL_PER_CHAR);
        assertEquals("abcd\nefgh\nij", clamped.text());
        assertFalse(clamped.truncated());
    }

    @Test
    public void singleLineJoinsParagraphsBeforeCutting() {
        BannerTextClamp.Clamped clamped = BannerTextClamp.clamp("Dataform:\n• first\n• second", 1, 18, ONE_PIXEL_PER_CHAR);
        assertEquals("Dataform: • fir...", clamped.text());
        assertTrue(clamped.truncated());
    }

    @Test
    public void unknownWidthLeavesTheTextUntouched() {
        BannerTextClamp.Clamped clamped = BannerTextClamp.clamp("aaa bbb ccc ddd", 1, 0, ONE_PIXEL_PER_CHAR);
        assertEquals("aaa bbb ccc ddd", clamped.text());
        assertFalse(clamped.truncated());
    }
}
