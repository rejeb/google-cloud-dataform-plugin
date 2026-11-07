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
package io.github.rejeb.dataform.language.gcp.execution.workflow.runconfig.ui;

import io.github.rejeb.dataform.language.gcp.execution.workflow.runconfig.ui.StatementErrorLocator.Position;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static io.github.rejeb.dataform.language.gcp.execution.workflow.runconfig.ui.StatementErrorLocator.locate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class StatementErrorLocatorTest {

    private static final String SCRIPT =
            "BEGIN\n  CREATE SCHEMA IF NOT EXISTS x;\n  SELECT 1;\n  MERGE t\n  USING s\n  ON SUM(a);\nEND";
    private static final List<String> STATEMENTS =
            List.of("CREATE SCHEMA IF NOT EXISTS x", "SELECT 1", "MERGE t\n  USING s\n  ON SUM(a)");

    @Test
    void aPositionInsideTheStatementIsMadeRelativeToIt() {
        assertEquals(new Position(3, 6), locate("No matching signature for SUM at [6:6]", SCRIPT, STATEMENTS, 2));
    }

    @Test
    void theFirstLineIsShiftedByTheStatementColumn() {
        assertEquals(new Position(1, 1), locate("x at [4:3]", SCRIPT, STATEMENTS, 2));
        assertEquals(new Position(1, 7), locate("x at [4:9]", SCRIPT, STATEMENTS, 2));
    }

    @Test
    void aPositionInAnotherStatementIsIgnored() {
        assertNull(locate("x at [2:3]", SCRIPT, STATEMENTS, 2));
    }

    @Test
    void repeatedStatementsAreFoundInOrder() {
        assertEquals(new Position(1, 1), locate("x at [2:1]", "SELECT 1;\nSELECT 1;", List.of("SELECT 1", "SELECT 1"), 1));
        assertNull(locate("x at [2:1]", "SELECT 1;\nSELECT 1;", List.of("SELECT 1", "SELECT 1"), 0));
    }

    @Test
    void withoutScriptOrStatementThereIsNoPosition() {
        assertNull(locate("x at [6:6]", null, STATEMENTS, 2));
        assertNull(locate("x at [6:6]", SCRIPT, List.of("a", "b", "MERGE u"), 2));
        assertNull(locate("x at [6:6]", SCRIPT, Arrays.asList("a", "b", null), 2));
        assertNull(locate("x at [6:6]", SCRIPT, STATEMENTS, 5));
    }

    @Test
    void aMessageWithoutPositionGivesNoPosition() {
        assertNull(locate("Access denied", SCRIPT, STATEMENTS, 2));
        assertNull(locate(null, SCRIPT, STATEMENTS, 2));
    }

    @Test
    void windowsLineSeparatorsAreNormalised() {
        assertEquals(new Position(3, 6), locate("x at [6:6]", SCRIPT.replace("\n", "\r\n"), STATEMENTS, 2));
    }

    @Test
    void theLastPositionOfTheMessageIsUsed() {
        assertEquals(new Position(3, 6), locate("near [1:1]: x at [6:6]", SCRIPT, STATEMENTS, 2));
    }

    @Test
    void thePositionSuffixIsDroppedFromTheShownMessage() {
        assertEquals("No matching signature for SUM",
                StatementErrorLocator.withoutPosition("No matching signature for SUM at [7:5]"));
        assertEquals("Access denied", StatementErrorLocator.withoutPosition("Access denied"));
    }
}
