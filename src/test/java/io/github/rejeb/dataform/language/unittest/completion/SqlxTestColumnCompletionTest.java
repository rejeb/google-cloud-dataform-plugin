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
package io.github.rejeb.dataform.language.unittest.completion;

import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.diagnostics.compile.ConfigSchemaFixture;
import io.github.rejeb.dataform.language.unittest.UnitTestGraphFixture;
import io.github.rejeb.dataform.language.unittest.UnitTestSchemaFixture;

import java.util.List;

public class SqlxTestColumnCompletionTest extends BasePlatformTestCase {

    private static final String CONFIG = "config {\n  type: \"test\",\n  dataset: \"orders\"\n}\n\n";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        ConfigSchemaFixture.install(getProject(), getTestRootDisposable());
        UnitTestGraphFixture.install(getProject(), getTestRootDisposable());
        UnitTestSchemaFixture.install(getProject(), getTestRootDisposable());
    }

    private List<String> complete(String path, String text) {
        PsiFile file = myFixture.addFileToProject(path, text);
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        myFixture.completeBasic();
        List<String> lookups = myFixture.getLookupElementStrings();
        return lookups == null ? List.of() : lookups;
    }

    private List<String> completeTest(String text) {
        return complete("definitions/tests/test_orders.sqlx", text);
    }

    public void testAnInputAliasOffersTheColumnsOfTheInputTable() {
        List<String> lookups = completeTest(CONFIG + "input \"raw_orders\" {\n  SELECT 'x' AS <caret>\n}\n\nSELECT 1 AS order_id\n");
        assertTrue("got " + lookups, lookups.size() >= 2);
        assertEquals(List.of("id", "amount"), lookups.subList(0, 2));
    }

    public void testAnExpectedAliasOffersTheColumnsOfTheTestedDatasetMinusTheUsedOnes() {
        List<String> lookups = completeTest(CONFIG + "SELECT '' AS order_id, 0 AS <caret>\n");
        assertTrue("got " + lookups, lookups.containsAll(List.of("total", "address", "items")));
        assertFalse("got " + lookups, lookups.contains("order_id"));
    }

    public void testAStructFieldOffersTheFieldsOfItsColumn() {
        List<String> lookups = completeTest(CONFIG + "SELECT STRUCT('Paris' AS <caret>) AS address\n");
        assertTrue("got " + lookups, lookups.size() >= 2);
        assertEquals(List.of("city", "geo"), lookups.subList(0, 2));
    }

    public void testANestedStructFieldOffersTheFieldsOfTheInnerRecord() {
        List<String> lookups = completeTest(CONFIG + "SELECT STRUCT(STRUCT(0.0 AS <caret>) AS geo) AS address\n");
        assertEquals(List.of("lat"), lookups);
    }

    public void testAnArrayOfStructsOffersTheElementFields() {
        List<String> lookups = completeTest(CONFIG + "SELECT [STRUCT('a' AS <caret>)] AS items\n");
        assertTrue("got " + lookups, lookups.size() >= 2);
        assertEquals(List.of("sku", "qty"), lookups.subList(0, 2));
    }

    public void testAStructAliasRanksRecordColumnsFirst() {
        List<String> lookups = completeTest(CONFIG + "SELECT STRUCT('p' AS city) AS <caret>\n");
        assertFalse("got " + lookups, lookups.isEmpty());
        assertEquals("got " + lookups, "address", lookups.get(0));
    }

    public void testNothingIsOfferedForAStructWithoutAlias() {
        List<String> lookups = completeTest(CONFIG + "SELECT STRUCT('Paris' AS <caret>)\n");
        assertFalse("got " + lookups, lookups.contains("city"));
        assertFalse("got " + lookups, lookups.contains("order_id"));
    }

    public void testNothingIsOfferedOutsideAnAlias() {
        List<String> lookups = completeTest(CONFIG + "SELECT <caret>\n");
        assertFalse("got " + lookups, lookups.contains("order_id"));
    }

    public void testNothingIsOfferedOutsideATest() {
        List<String> lookups = complete("definitions/orders.sqlx",
                "config { type: \"table\" }\n\nSELECT 1 AS <caret>\n");
        assertFalse("got " + lookups, lookups.contains("order_id"));
    }
}
