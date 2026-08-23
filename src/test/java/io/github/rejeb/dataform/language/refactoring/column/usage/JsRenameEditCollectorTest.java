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
 * A Dataform project writes column names in JavaScript, as strings a helper turns into SQL. Those
 * places are found by matching the name and are reported for review rather than as certain.
 */
public class JsRenameEditCollectorTest extends BasePlatformTestCase {

    private List<ColumnRenameEdit> collect(String oldName, String newName, PsiFile... scope) {
        return JsRenameEditCollector.collect(getProject(), oldName, newName, List.of(scope));
    }

    private String presentations(List<ColumnRenameEdit> edits) {
        return edits.stream()
                .map(edit -> edit.kind() + "/" + edit.risk() + "/" + edit.replacement())
                .collect(Collectors.joining(" "));
    }

    public void testAStringThatIsExactlyTheColumnNameIsCertain() {
        myFixture.addFileToProject("includes/helpers.js",
                "function whereClause() { return incremental(\"order_ts\", 3); }");

        List<ColumnRenameEdit> edits = collect("order_ts", "loaded_at");

        assertEquals("JS_STRING/CERTAIN/\"loaded_at\"", presentations(edits));
    }

    public void testANameInsideALongerStringIsReportedForReview() {
        myFixture.addFileToProject("includes/helpers.js",
                "const condition = \"order_ts IS NOT NULL\";");

        List<ColumnRenameEdit> edits = collect("order_ts", "loaded_at");

        assertEquals(1, edits.size());
        assertEquals(ColumnRenameEdit.Risk.HEURISTIC, edits.getFirst().risk());
        assertTrue("text found by matching is reviewed apart from code",
                edits.getFirst().isNonCode());
    }

    public void testANameThatIsPartOfALongerNameIsNotAMatch() {
        myFixture.addFileToProject("includes/helpers.js",
                "const columns = [\"order_ts_utc\", \"first_order_ts_at\"];");

        assertTrue("a column name is matched as a whole identifier", collect("order_ts", "x").isEmpty());
    }

    public void testAModulePathIsNeverAColumnName() {
        myFixture.addFileToProject("includes/helpers.js",
                "const helpers = require(\"includes/order_ts\");");

        assertTrue("the path of a require is not a column name", collect("order_ts", "x").isEmpty());
    }

    public void testTheJavaScriptOfASqlxFileIsSearchedToo() {
        PsiFile action = myFixture.addFileToProject("definitions/action.sqlx",
                "config { type: \"table\" }\n\njs {\n  const keys = [\"order_id\"];\n}\n\nSELECT 1 AS x\n");

        List<ColumnRenameEdit> edits = collect("order_id", "order_ref", action);

        assertEquals("JS_STRING/CERTAIN/\"order_ref\"", presentations(edits));
        assertEquals("the edit is written in the SQLX file that holds the block",
                "action.sqlx", edits.getFirst().file().getName());
    }

    public void testTheJavaScriptOfAnActionTheRenameDoesNotTouchIsLeftAlone() {
        myFixture.addFileToProject("definitions/other.sqlx",
                "config { type: \"table\" }\n\njs {\n  const keys = [\"order_id\"];\n}\n\nSELECT 1 AS x\n");

        assertTrue("a column name is not unique in a project: the block of an action the rename has "
                        + "nothing to do with names another column",
                collect("order_id", "order_ref").isEmpty());
    }

    public void testAJavaScriptPropertyOfTheSameNameIsReportedForReview() {
        myFixture.addFileToProject("includes/helpers.js",
                "const mapping = { order_id: 1 };");

        List<ColumnRenameEdit> edits = collect("order_id", "order_ref");

        assertEquals("JS_IDENTIFIER/HEURISTIC/order_ref", presentations(edits));
    }
}
