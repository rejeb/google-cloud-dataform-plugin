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
package io.github.rejeb.dataform.language.diagnostics.sql.mapping;

import com.intellij.openapi.util.TextRange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class SqlTokenLocatorTest {

    @Test
    public void theTokenAtAnOffsetIsTheWholeIdentifier() {
        assertEquals(new TextRange(7, 17), SqlTokenLocator.locate("SELECT custmer_id FROM t", 9, null));
    }

    @Test
    public void aBacktickedNameIsOneToken() {
        assertEquals(new TextRange(7, 17), SqlTokenLocator.locate("SELECT `order id` FROM t", 7, "order id"));
    }

    @Test
    public void theNamedTokenIsCheckedIgnoringCase() {
        assertEquals(new TextRange(7, 17), SqlTokenLocator.locate("SELECT CUSTMER_ID FROM t", 7, "custmer_id"));
    }

    @Test
    public void aMisplacedColumnFallsBackToTheNameOnTheSameLine() {
        assertEquals(new TextRange(10, 20), SqlTokenLocator.locate("SELECT a,\tcustmer_id FROM t", 9, "custmer_id"));
    }

    @Test
    public void aNameFoundOnlyElsewhereIsUsedWhenItOccursOnce() {
        String sql = "SELECT a\nFROM t\nWHERE custmer_id = 1";

        assertEquals(TextRange.from(sql.indexOf("custmer_id"), 10), SqlTokenLocator.locate(sql, 3, "custmer_id"));
    }

    @Test
    public void aNameThatCannotBeFoundIsNowhere() {
        assertNull(SqlTokenLocator.locate("SELECT customer_id FROM t", 7, "custmer_id"));
    }

    @Test
    public void aDottedNameCoversTheWholePath() {
        assertEquals(new TextRange(7, 15),
                SqlTokenLocator.locate("SELECT o.amount FROM t o GROUP BY o.id", 7, "o.amount"));
    }

    @Test
    public void aNameInsideAPathIsFoundByItsOwnPart() {
        assertEquals(new TextRange(9, 18), SqlTokenLocator.locate("SELECT o.order_tss FROM t o", 7, "order_tss"));
    }

    @Test
    public void anErrorPastTheLastTokenSitsAtItsEnd() {
        String sql = "SELECT a FROM (\n";

        assertEquals(TextRange.from(15, 0), SqlTokenLocator.locate(sql, sql.length(), null));
    }

    @Test
    public void punctuationIsASingleCharacter() {
        assertEquals(TextRange.from(8, 1), SqlTokenLocator.locate("SELECT a, FROM t", 8, ","));
    }

    @Test
    public void aStringLiteralRunsToItsClosingQuote() {
        assertEquals(new TextRange(7, 12), SqlTokenLocator.locate("SELECT 'abc' AS x", 7, null));
    }
}
