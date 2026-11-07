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
package io.github.rejeb.dataform.language.compilation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CodeFrameTest {

    @Test
    void testMiddleLine() {
        assertEquals("""
                          1 | a
                        > 2 | SELECT x
                            |        ^
                          3 | b""",
                CodeFrame.of("a\nSELECT x\nb", 1, 7, 1));
    }

    @Test
    void testFirstLine() {
        assertEquals("""
                        > 1 | SELECT x
                            | ^^^^^^
                          2 | b""",
                CodeFrame.of("SELECT x\nb", 0, 0, 6));
    }

    @Test
    void testLastLineAndAnEmptyRangeShowOneCaret() {
        assertEquals("""
                          1 | a
                        > 2 | b""" + "\n    |  ^",
                CodeFrame.of("a\nb", 1, 1, 0));
    }

    @Test
    void testNumbersAreRightAligned() {
        String text = "l1\nl2\nl3\nl4\nl5\nl6\nl7\nl8\nl9\nl10";

        assertEquals("""
                           9 | l9
                        > 10 | l10
                             | ^""",
                CodeFrame.of(text, 9, 0, 1));
    }

    @Test
    void testTabsBeforeTheColumnKeepTheCaretAligned() {
        assertEquals("> 1 | \tx\n    | \t^", CodeFrame.of("\tx", 0, 1, 1));
    }

    @Test
    void testALineOutsideTheTextGivesNothing() {
        assertEquals("", CodeFrame.of("a", 3, 0, 1));
    }
}
