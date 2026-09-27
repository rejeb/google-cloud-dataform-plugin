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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class MappedTextTest {

    @Test
    public void anIdentityTracesEveryOffsetToItself() {
        MappedText text = MappedText.identity("q", "SELECT a");

        assertEquals("SELECT a", text.text());
        assertEquals(new MappedText.SourcePosition("q", 7, true), text.toSource(7));
        assertEquals(new MappedText.SourcePosition("q", 8, true), text.toSource(8));
    }

    @Test
    public void copiedPiecesTraceExactlyAndSyntheticOnesTraceNowhere() {
        MappedText text = MappedText.builder()
                .source("q", "SELECT a FROM t")
                .synthetic("WITH x AS (SELECT 1)\n")
                .copy("q", 0, 15)
                .build();

        assertEquals("WITH x AS (SELECT 1)\nSELECT a FROM t", text.text());
        assertNull(text.toSource(3));
        assertEquals(new MappedText.SourcePosition("q", 7, true), text.toSource(21 + 7));
    }

    @Test
    public void aReplacementTracesToTheStartOfWhatItReplaced() {
        MappedText text = MappedText.builder()
                .source("q", "FROM p.d.orders AS o")
                .copy("q", 0, 5)
                .replaceWith("_df_orders", "q", 5, 15)
                .copy("q", 15, 20)
                .build();

        assertEquals("FROM _df_orders AS o", text.text());
        assertEquals(new MappedText.SourcePosition("q", 5, false), text.toSource(9));
        assertEquals(new MappedText.SourcePosition("q", 19, true), text.toSource(19));
    }

    @Test
    public void aSubTextKeepsItsTrace() {
        MappedText text = MappedText.builder()
                .source("q", "-- c\nWITH a AS (SELECT 1) SELECT * FROM a")
                .synthetic("WITH\n")
                .copy("q", 0, 41)
                .build();

        MappedText tail = text.subText(14, text.text().length());

        assertEquals(" a AS (SELECT 1) SELECT * FROM a", tail.text());
        assertEquals(new MappedText.SourcePosition("q", 9, true), tail.toSource(0));
    }

    @Test
    public void anAppendedTextIsShiftedByWhatPrecedesIt() {
        MappedText query = MappedText.identity("q", "SELECT a");
        MappedText text = MappedText.builder()
                .source("pre", "DECLARE x INT64;")
                .copy("pre", 0, 16)
                .synthetic("\n")
                .append(query)
                .synthetic(";")
                .build();

        assertEquals("DECLARE x INT64;\nSELECT a;", text.text());
        assertEquals(new MappedText.SourcePosition("pre", 8, true), text.toSource(8));
        assertEquals(new MappedText.SourcePosition("q", 7, true), text.toSource(24));
        assertNull(text.toSource(25));
        assertEquals("SELECT a", text.sourceText("q"));
    }

    @Test
    public void aSourceCannotBeDeclaredTwiceWithDifferentTexts() {
        MappedText.Builder builder = MappedText.builder().source("q", "a");

        assertThrows(IllegalArgumentException.class, () -> builder.source("q", "b"));
    }
}
