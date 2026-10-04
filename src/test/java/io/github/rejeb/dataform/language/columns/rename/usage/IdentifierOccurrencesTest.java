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
package io.github.rejeb.dataform.language.columns.rename.usage;

import com.intellij.openapi.util.TextRange;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class IdentifierOccurrencesTest {

    @Test
    public void findsEveryWholeIdentifier() {
        List<TextRange> ranges = IdentifierOccurrences.of("id, t.id, (id)", "id");
        assertEquals(List.of(new TextRange(0, 2), new TextRange(6, 8), new TextRange(11, 13)), ranges);
    }

    @Test
    public void ignoresMatchesInsideLongerIdentifiers() {
        assertTrue(IdentifierOccurrences.of("order_id_2 + xorder_id + order_id$", "order_id").isEmpty());
        assertTrue(IdentifierOccurrences.of("order_id1", "order_id").isEmpty());
    }

    @Test
    public void matchesAtTextBoundaries() {
        assertEquals(List.of(new TextRange(0, 3)), IdentifierOccurrences.of("abc", "abc"));
    }

    @Test
    public void emptyNameMatchesNothing() {
        assertTrue(IdentifierOccurrences.of("abc", "").isEmpty());
    }

    @Test
    public void adjacentOccurrencesDoNotOverlap() {
        assertEquals(List.of(new TextRange(0, 1), new TextRange(2, 3)), IdentifierOccurrences.of("a a", "a"));
        assertTrue(IdentifierOccurrences.of("aa", "a").isEmpty());
    }
}
