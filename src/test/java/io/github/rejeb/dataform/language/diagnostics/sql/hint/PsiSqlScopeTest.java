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
package io.github.rejeb.dataform.language.diagnostics.sql.hint;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import io.github.rejeb.dataform.language.schema.sql.DataformProjectFixture;
import io.github.rejeb.dataform.language.schema.sql.DataformTableSchemaService;

import java.util.List;

public class PsiSqlScopeTest extends DataformProjectFixture {

    private PsiFile configure(String sql) {
        PsiFile file = myFixture.addFileToProject("definitions/scope_probe.sqlx",
                "config { type: \"table\" }\n\n" + sql);
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        return myFixture.getFile();
    }

    public void testEachSourceIsReadByItsAliasWithItsColumns() {
        PsiFile file = configure("SELECT\n  custmer_id\nFROM ${ref(\"silver_orders\")} AS o\n"
                + "JOIN ${ref(\"silver_customers\")} c ON c.customer_id = o.customer_id\n");

        SqlScope scope = PsiSqlScope.at(file, file.getText().indexOf("custmer_id"));

        assertEquals(List.of("o", "c"), List.copyOf(scope.columnsBySource().keySet()));
        assertEquals(List.of("order_id", "customer_id", "order_ts", "order_status"), scope.columnsBySource().get("o"));
        assertTrue(scope.columns().contains("full_name"));
    }

    public void testTheColumnsOfASourceFollowASchemaRefresh() {
        PsiFile file = configure("SELECT\n  custmer_id\nFROM ${ref(\"silver_orders\")} AS o\n");
        int at = file.getText().indexOf("custmer_id");
        assertEquals(List.of("order_id", "customer_id", "order_ts", "order_status"),
                PsiSqlScope.at(file, at).columnsBySource().get("o"));
        DataformTableSchemaService.State refreshed = new DataformTableSchemaService.State();
        refreshed.schemaCacheJson = "{\"proj.ds.silver_orders\":{\"columns\":["
                + "{\"name\":\"order_id\",\"type\":\"STRING\",\"mode\":\"NULLABLE\",\"subFields\":[]},"
                + "{\"name\":\"customer_id\",\"type\":\"STRING\",\"mode\":\"NULLABLE\",\"subFields\":[]}],"
                + "\"lastModified\":0,\"fileName\":\"definitions/silver_orders.sqlx\"}}";

        DataformTableSchemaService.getInstance(getProject()).loadState(refreshed);

        assertEquals(List.of("order_id", "customer_id"), PsiSqlScope.at(file, at).columnsBySource().get("o"));
    }

    public void testTheSqlOfAQueryStartsPastTheBlocksBeforeIt() {
        PsiFile file = configure("js {\n  const re = /\\(/;\n}\n\nSELECT\n  custmer_id\nFROM ${ref(\"silver_orders\")} AS o\n");
        String text = file.getText();

        int start = PsiSqlScope.at(file, text.indexOf("custmer_id")).sqlStart();

        assertTrue(String.valueOf(start), start > text.indexOf("}\n\nSELECT") && start <= text.indexOf("SELECT"));
    }

    public void testACteIsReadByTheColumnsItsQueryNames() {
        PsiFile file = configure("WITH x AS (SELECT order_id, customer_id AS cid FROM ${ref(\"silver_orders\")})\n"
                + "SELECT\n  cidd\nFROM x\n");

        SqlScope scope = PsiSqlScope.at(file, file.getText().indexOf("cidd"));

        assertEquals(List.of("order_id", "cid"), scope.columnsBySource().get("x"));
    }

    public void testTheEndOfTheGroupByIsWhereAColumnIsAdded() {
        PsiFile file = configure("SELECT customer_id, order_ts\nFROM ${ref(\"silver_orders\")}\nGROUP BY customer_id\n");

        SqlScope scope = PsiSqlScope.at(file, file.getText().indexOf("order_ts"));

        String text = file.getText();
        assertEquals(text.indexOf("GROUP BY customer_id") + "GROUP BY customer_id".length(), scope.groupByEnd());
    }

    public void testAQueryWithoutGroupByHasNoPlaceToAddOne() {
        PsiFile file = configure("SELECT customer_id\nFROM ${ref(\"silver_orders\")}\n");

        assertEquals(-1, PsiSqlScope.at(file, file.getText().indexOf("customer_id")).groupByEnd());
    }

    public void testActionsAndFunctionsAreOffered() {
        PsiFile file = configure("SELECT customer_id\nFROM ${ref(\"silver_orders\")}\n");

        SqlScope scope = PsiSqlScope.at(file, file.getText().indexOf("customer_id"));

        assertTrue(scope.actionNames().contains("silver_orders"));
        assertTrue(scope.functions().stream().anyMatch("DATE_DIFF"::equalsIgnoreCase));
    }

    public void testTheSelectItemsOfTheMainQueryAreFoundByName() {
        PsiFile file = configure("SELECT order_id, customer_id AS order_id\nFROM ${ref(\"silver_orders\")}\n");

        List<SqlScope.SelectItem> items = PsiSqlScope.mainQueryOf(file).selectItemsNamed("order_id");

        String text = file.getText();
        assertEquals(2, items.size());
        assertEquals(TextRange.from(text.indexOf("order_id"), 8), items.get(0).range());
        assertNull(items.get(0).alias());
        assertEquals(TextRange.from(text.lastIndexOf("order_id"), 8), items.get(1).alias());
    }
}
