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
package io.github.rejeb.dataform.language.refactoring.column.usage;

import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.List;
import java.util.stream.Collectors;

/**
 * The config block of an action names its columns in several ways, and a rename has to reach all of
 * them.
 */
public class ConfigRenameEditCollectorTest extends BasePlatformTestCase {

    private List<ColumnRenameEdit> collect(String config, String oldName, String newName) {
        PsiFile file = myFixture.configureByText("action.sqlx",
                "config {\n" + config + "\n}\n\nSELECT 1 AS order_id\n");
        return ConfigRenameEditCollector.collect(file, oldName, newName);
    }

    private String kinds(List<ColumnRenameEdit> edits) {
        return edits.stream().map(edit -> edit.kind().name()).collect(Collectors.joining(","));
    }

    private String replacements(List<ColumnRenameEdit> edits) {
        return edits.stream().map(ColumnRenameEdit::replacement).collect(Collectors.joining(","));
    }

    public void testAKeyOfTheColumnsMapIsRenamed() {
        List<ColumnRenameEdit> edits = collect(
                "  type: \"table\",\n  columns: { order_id: \"the order\" }", "order_id", "order_ref");

        assertEquals("CONFIG_COLUMN_KEY", kinds(edits));
        assertEquals("order_ref", replacements(edits));
    }

    public void testAKeyThatIsNoIdentifierIsQuoted() {
        List<ColumnRenameEdit> edits = collect(
                "  columns: { order_id: \"the order\" }", "order_id", "order ref");

        assertEquals("\"order ref\"", replacements(edits));
    }

    public void testTheColumnNamesOfClusterByAreRenamed() {
        List<ColumnRenameEdit> edits = collect(
                "  bigquery: { clusterBy: [\"customer_id\", \"order_id\"] }", "order_id", "order_ref");

        assertEquals("CONFIG_COLUMN_NAME", kinds(edits));
        assertEquals("\"order_ref\"", replacements(edits));
    }

    public void testTheUniqueKeyAndTheAssertionsAreRenamed() {
        List<ColumnRenameEdit> edits = collect(
                "  uniqueKey: [\"order_id\"],\n  assertions: { nonNull: [\"order_id\"] }",
                "order_id", "order_ref");

        assertEquals(2, edits.size());
        assertEquals("CONFIG_COLUMN_NAME,CONFIG_COLUMN_NAME", kinds(edits));
    }

    public void testTheFieldOfThePartitionByObjectIsRenamed() {
        List<ColumnRenameEdit> edits = collect(
                "  bigquery: { partitionBy: { field: \"order_ts\", type: \"DAY\" } }",
                "order_ts", "loaded_at");

        assertEquals("CONFIG_COLUMN_NAME", kinds(edits));
        assertEquals("\"loaded_at\"", replacements(edits));
    }

    public void testTheColumnInsideThePartitionExpressionIsRenamed() {
        List<ColumnRenameEdit> edits = collect(
                "  bigquery: { partitionBy: \"TIMESTAMP_TRUNC(order_ts, DAY)\" }",
                "order_ts", "loaded_at");

        assertEquals("CONFIG_PARTITION_EXPRESSION", kinds(edits));
        assertEquals("loaded_at", replacements(edits));
        assertEquals(ColumnRenameEdit.Risk.CERTAIN, edits.getFirst().risk());
    }

    public void testARowConditionIsRenamedAsTextThatHasToBeReviewed() {
        List<ColumnRenameEdit> edits = collect(
                "  assertions: { rowConditions: [\"order_id IS NOT NULL\"] }", "order_id", "order_ref");

        assertEquals("CONFIG_ROW_CONDITION", kinds(edits));
        assertEquals(ColumnRenameEdit.Risk.HEURISTIC, edits.getFirst().risk());
        assertTrue("a row condition is a place the user reviews", edits.getFirst().isNonCode());
    }

    public void testANameThatOnlyLooksLikeTheColumnIsLeftAlone() {
        List<ColumnRenameEdit> edits = collect(
                "  bigquery: { clusterBy: [\"order_id_2\"] },\n  description: \"order_id\"",
                "order_id", "order_ref");

        assertTrue("neither a longer name nor a description is a column name", edits.isEmpty());
    }
}
