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
package io.github.rejeb.dataform.language.unittest.schema;

import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.diagnostics.compile.ConfigSchemaFixture;
import io.github.rejeb.dataform.language.psi.SqlxInputBlock;
import io.github.rejeb.dataform.language.psi.SqlxSqlBlock;
import io.github.rejeb.dataform.language.schema.sql.model.ColumnInfo;
import io.github.rejeb.dataform.language.unittest.UnitTestGraphFixture;
import io.github.rejeb.dataform.language.unittest.UnitTestSchemaFixture;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class TestSchemaResolverTest extends BasePlatformTestCase {

    private static final String TEST_CONFIG = "config {\n  type: \"test\",\n  dataset: \"orders\"\n}\n\n";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        ConfigSchemaFixture.install(getProject(), getTestRootDisposable());
        UnitTestGraphFixture.install(getProject(), getTestRootDisposable());
        UnitTestSchemaFixture.install(getProject(), getTestRootDisposable());
    }

    private TestSchemaResolver resolver() {
        return TestSchemaResolver.getInstance(getProject());
    }

    private PsiFile file(String text) {
        return myFixture.addFileToProject("definitions/tests/orders_test.sqlx", text);
    }

    private SqlxSqlBlock inputBody(PsiFile file, int index) {
        return PsiTreeUtil.findChildrenOfType(file, SqlxInputBlock.class).stream().toList().get(index).content();
    }

    private SqlxSqlBlock mainBody(PsiFile file) {
        return PsiTreeUtil.getChildrenOfTypeAsList(file, SqlxSqlBlock.class).getLast();
    }

    private static List<String> names(Optional<TestBlockSchema> schema) {
        return schema.map(s -> s.columns().stream().map(ColumnInfo::name).toList()).orElse(List.of());
    }

    public void testAnInputNamedByItsBareNameResolvesToItsTable() {
        PsiFile file = file(TEST_CONFIG + "input \"raw_orders\" {\n  SELECT 1 AS id\n}\n\nSELECT 1 AS order_id\n");
        Optional<TestBlockSchema> schema = resolver().resolve(inputBody(file, 0));
        assertEquals(TestBlockKind.INPUT, schema.orElseThrow().kind());
        assertEquals(List.of("id", "amount"), names(schema));
    }

    public void testAnInputNamedBySchemaAndNameResolvesToItsTable() {
        PsiFile file = file(TEST_CONFIG + "input \"raw\", \"raw_orders\" {\n  SELECT 1 AS id\n}\n\nSELECT 1 AS order_id\n");
        assertEquals(List.of("id", "amount"), names(resolver().resolve(inputBody(file, 0))));
    }

    public void testTheExpectedQueryResolvesToTheTestedDataset() {
        PsiFile file = file(TEST_CONFIG + "input \"raw_orders\" {\n  SELECT 1 AS id\n}\n\nSELECT 1 AS order_id\n");
        Optional<TestBlockSchema> schema = resolver().resolve(mainBody(file));
        assertEquals(TestBlockKind.EXPECTED, schema.orElseThrow().kind());
        assertEquals(List.of("order_id", "total", "address", "items"), names(schema));
    }

    public void testTheTestedDatasetMayBeGivenAsATargetObject() {
        PsiFile file = file("config {\n  type: \"test\",\n  dataset: { schema: \"d\", name: \"orders\" }\n}\n\nSELECT 1 AS order_id\n");
        assertEquals("orders", resolver().resolve(mainBody(file)).orElseThrow().target().getName());
    }

    public void testATargetObjectPicksTheTableOfItsSchemaAmongHomonyms() {
        UnitTestGraphFixture.install(getProject(), getTestRootDisposable(), """
                {
                  "tables": [
                    {"type": "table", "target": {"database": "p", "schema": "staging", "name": "customers"},
                     "fileName": "definitions/staging/customers.sqlx", "disabled": false},
                    {"type": "table", "target": {"database": "p", "schema": "mart", "name": "customers"},
                     "fileName": "definitions/mart/customers.sqlx", "disabled": false}
                  ],
                  "graphErrors": {"compilationErrors": []}
                }""");
        UnitTestSchemaFixture.install(getProject(), getTestRootDisposable(), Map.of(
                "p.staging.customers", UnitTestSchemaFixture.RAW_ORDERS,
                "p.mart.customers", UnitTestSchemaFixture.CUSTOMERS));
        PsiFile file = file("config {\n  type: \"test\",\n  dataset: { schema: \"mart\", name: \"customers\" }\n}\n\nSELECT 1 AS customer_id\n");

        Optional<TestBlockSchema> schema = resolver().resolve(mainBody(file));

        assertEquals("mart", schema.orElseThrow().target().getSchema());
        assertEquals(List.of("customer_id", "name"), names(schema));
    }

    public void testAFileThatIsNoTestResolvesNothing() {
        PsiFile file = myFixture.addFileToProject("definitions/x.sqlx",
                "config { type: \"table\", dataset: \"orders\" }\n\nSELECT 1 AS order_id\n");
        assertEquals(Optional.empty(), resolver().resolve(mainBody(file)));
    }

    public void testAnUnknownOrSchemalessTargetResolvesNothing() {
        PsiFile file = file(TEST_CONFIG + "input \"stats\" {\n  SELECT 1 AS id\n}\n\ninput \"nope\" {\n  SELECT 1\n}\n\nSELECT 1\n");
        assertEquals(Optional.empty(), resolver().resolve(inputBody(file, 0)));
        assertEquals(Optional.empty(), resolver().resolve(inputBody(file, 1)));
    }

    public void testWithoutGraphNothingResolves() {
        UnitTestGraphFixture.clear(getProject());
        PsiFile file = file(TEST_CONFIG + "SELECT 1 AS order_id\n");
        assertEquals(Optional.empty(), resolver().resolve(mainBody(file)));
    }

    public void testTheInputLabelLeadsToTheInputBody() {
        PsiFile file = file(TEST_CONFIG + "input \"raw_orders\" {\n  SELECT 1 AS id\n}\n\nSELECT 1\n");
        int label = file.getText().indexOf("raw_orders");
        assertEquals(List.of("id", "amount"), names(resolver().resolveAt(file.findElementAt(label))));
    }
}
