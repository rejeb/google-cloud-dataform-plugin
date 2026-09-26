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
package io.github.rejeb.dataform.language.diagnostics.sql.bigquery;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class QueryPositionsTest {

    @Test
    public void aColumnOnTheFirstLine() {
        assertEquals(7, QueryPositions.offsetOf("SELECT custmer_id", 1, 8));
    }

    @Test
    public void aColumnOnALaterLine() {
        assertEquals(9, QueryPositions.offsetOf("SELECT\n  custmer_id", 2, 3));
    }

    @Test
    public void windowsLineEndsCountAsOneBreak() {
        assertEquals(10, QueryPositions.offsetOf("SELECT\r\n  custmer_id", 2, 3));
    }

    @Test
    public void aLoneCarriageReturnIsABreak() {
        assertEquals(9, QueryPositions.offsetOf("SELECT\r  custmer_id", 2, 3));
    }

    @Test
    public void tabsAreExpandedToTheNextMultipleOfEight() {
        assertEquals(1, QueryPositions.offsetOf("\tcustmer_id", 1, 9));
        assertEquals(3, QueryPositions.offsetOf("ab\tc", 1, 9));
    }

    @Test
    public void aColumnPastTheEndOfItsLineIsTheEndOfTheLine() {
        assertEquals(6, QueryPositions.offsetOf("SELECT\nx", 1, 40));
    }

    @Test
    public void aPositionOutsideTheQueryIsNowhere() {
        assertEquals(-1, QueryPositions.offsetOf("SELECT 1", 3, 1));
        assertEquals(-1, QueryPositions.offsetOf("SELECT 1", 0, 1));
    }
}
