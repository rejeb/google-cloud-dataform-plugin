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
package io.github.rejeb.dataform.language.unittest.generation;

import io.github.rejeb.dataform.language.unittest.schema.TestBlockKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestBlockBodyTest {

    private static String apply(String block, TestBlockKind kind, String select) {
        TestBlockBody.Replacement r = TestBlockBody.replacement(block, kind, select);
        return block.substring(0, r.range().getStartOffset()) + r.text() + block.substring(r.range().getEndOffset());
    }

    @Test
    void anInputBodyIsReplacedWhole() {
        assertEquals("\n  SELECT\n    '' AS id\n", apply("\n  SELECT 1 AS id\n", TestBlockKind.INPUT, "SELECT\n    '' AS id"));
        assertEquals("\n  SELECT\n    '' AS id\n", apply("", TestBlockKind.INPUT, "SELECT\n    '' AS id"));
    }

    @Test
    void theExpectedQueryKeepsItsLeadingCommentsAndTrailingBreak() {
        assertEquals("\n\n-- Expected output\nSELECT\n  0 AS n\n",
                apply("\n\n-- Expected output\nSELECT 1 AS n\n\n", TestBlockKind.EXPECTED, "SELECT\n  0 AS n"));
    }

    @Test
    void anEmptyExpectedQueryIsFilled() {
        assertEquals("\n\nSELECT\n  0 AS n\n", apply("\n\n", TestBlockKind.EXPECTED, "SELECT\n  0 AS n"));
    }
}
