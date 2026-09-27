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
package io.github.rejeb.dataform.language.completion;

import com.google.gson.Gson;
import com.intellij.openapi.application.WriteAction;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.testing.ProjectStateInstaller;

import java.util.List;

/**
 * The arguments of {@code ref()} read from the end: name, schema, project. The first argument offers
 * the default project, the schemas, then the tables; the second the schemas and the tables, or only
 * the tables of the schema the first one names; the third only the tables.
 */
public class DataformRefCompletionTest extends BasePlatformTestCase {

    private static final String GRAPH = """
            {
              "projectConfig": {"defaultDatabase": "compiled-p"},
              "tables": [
                {"type": "table", "target": {"database": "p", "schema": "d_dev", "name": "orders"},
                 "canonicalTarget": {"database": "p", "schema": "d", "name": "orders"},
                 "fileName": "definitions/orders.sqlx"},
                {"type": "table", "target": {"database": "p", "schema": "mart", "name": "customers"},
                 "fileName": "definitions/customers.sqlx"},
                {"type": "view", "target": {"database": "p", "schema": "mart", "name": "payments"},
                 "fileName": "definitions/payments.sqlx"}
              ],
              "declarations": [
                {"target": {"database": "p", "schema": "raw", "name": "events"},
                 "fileName": "definitions/sources.js"}
              ]
            }""";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        ProjectStateInstaller.installGraph(getProject(), getTestRootDisposable(),
                new Gson().fromJson(GRAPH, CompiledGraph.class));
        myFixture.addFileToProject("workflow_settings.yaml", "defaultProject: p\ndefaultDataset: d\n");
    }

    public void testTheFirstArgumentOffersTheDefaultProjectThenTheSchemasThenTheTables() {
        List<String> lookups = complete("ref(\"<caret>\");");

        assertEquals("got " + lookups, List.of("p", "d", "mart", "raw"), lookups.subList(0, 4));
        assertTrue("got " + lookups, lookups.subList(4, lookups.size())
                .containsAll(List.of("orders", "customers", "payments", "events")));
    }

    public void testTheDefaultProjectFallsBackToTheCompiledOneWithoutWorkflowSettings() throws Exception {
        WriteAction.runAndWait(() -> myFixture.findFileInTempDir("workflow_settings.yaml").delete(this));
        List<String> lookups = complete("ref(\"<caret>\");");

        assertEquals("got " + lookups, "compiled-p", lookups.getFirst());
    }

    public void testSchemasAreTheOnesRefMatchesNotTheCompiledOnes() {
        List<String> lookups = complete("ref(\"<caret>\");");

        assertFalse("the schema suffix is not written in ref(), got " + lookups, lookups.contains("d_dev"));
    }

    public void testTheFirstOfTwoArgumentsAlsoOffersTheProjectAndSchemas() {
        List<String> lookups = complete("ref(\"<caret>\", \"customers\");");

        assertEquals("got " + lookups, List.of("p", "d", "mart", "raw"), lookups.subList(0, 4));
    }

    public void testTheSecondArgumentAfterAProjectOffersTheSchemasThenTheTables() {
        List<String> lookups = complete("ref(\"p\", \"<caret>\");");

        assertEquals("got " + lookups, List.of("d", "mart", "raw"), lookups.subList(0, 3));
        assertTrue("got " + lookups, lookups.subList(3, lookups.size())
                .containsAll(List.of("orders", "customers", "payments", "events")));
        assertFalse("got " + lookups, lookups.contains("p"));
    }

    public void testTheThirdArgumentOffersOnlyTheTablesOfTheSecondArgumentSchema() {
        List<String> lookups = complete("ref(\"p\", \"mart\", \"<caret>\");");

        assertEquals(List.of("customers", "payments"), lookups.stream().sorted().toList());
    }

    public void testTheThirdArgumentOffersEveryTableWhenTheSchemaIsNotALiteral() {
        List<String> lookups = complete("ref(\"p\", schemaName, \"<caret>\");");

        assertTrue("got " + lookups, lookups.containsAll(List.of("orders", "customers", "payments", "events")));
        assertFalse("got " + lookups, lookups.contains("mart"));
    }

    public void testTheSecondArgumentOffersOnlyTheTablesOfTheFirstArgumentSchema() {
        List<String> lookups = complete("ref(\"mart\", \"<caret>\");");

        assertEquals(List.of("customers", "payments"), lookups.stream().sorted().toList());
    }

    public void testTheSecondArgumentOffersTheSchemasAndTablesWhenTheFirstIsNotALiteral() {
        List<String> lookups = complete("ref(firstPart, \"<caret>\");");

        assertEquals("got " + lookups, List.of("d", "mart", "raw"), lookups.subList(0, 3));
        assertTrue("got " + lookups, lookups.containsAll(List.of("orders", "customers", "payments", "events")));
    }

    public void testSchemasAreOfferedInSqlxTemplates() {
        myFixture.configureByText("probe.sqlx", "config { type: \"view\" }\nSELECT * FROM ${ref(\"<caret>\")}");
        myFixture.completeBasic();
        List<String> lookups = myFixture.getLookupElementStrings();

        assertNotNull(lookups);
        assertEquals("got " + lookups, List.of("p", "d", "mart", "raw"), lookups.subList(0, 4));
    }

    private List<String> complete(String code) {
        myFixture.configureByText("query.js", code);
        myFixture.completeBasic();
        List<String> lookups = myFixture.getLookupElementStrings();
        assertNotNull("no completion popup for " + code, lookups);
        return lookups;
    }
}
