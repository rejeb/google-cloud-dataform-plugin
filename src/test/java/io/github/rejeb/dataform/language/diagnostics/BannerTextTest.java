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

public class BannerTextTest {

    @Test
    public void singleMessageJoinsTheHeaderLine() {
        assertEquals("Failed: boom", BannerText.of("Failed:", List.of("boom"), "?"));
    }

    @Test
    public void severalMessagesBecomeABulletedList() {
        assertEquals("Failed:\n• one\n• two",
                BannerText.of("Failed:", List.of("one", "two"), "?"));
    }

    @Test
    public void collapseSqueezesWhitespaceOntoOneLine() {
        assertEquals("a b c", BannerText.collapse("  a\n\n  b\t c ", "fallback"));
    }

    @Test
    public void collapseReplacesAnEmptyMessageWithTheFallback() {
        assertEquals("fallback", BannerText.collapse(" \n ", "fallback"));
        assertEquals("Failed: fallback", BannerText.of("Failed:", List.of(""), "fallback"));
    }
}
