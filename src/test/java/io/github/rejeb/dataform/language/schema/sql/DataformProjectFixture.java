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
package io.github.rejeb.dataform.language.schema.sql;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.sql.psi.SqlCompositeElementTypes;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.compilation.DataformCompilationService;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.compilation.model.CompiledTable;
import io.github.rejeb.dataform.language.compilation.model.Target;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Base for tests reading the {@code user_purchase} fixtures. Installs a compiled graph so that
 * {@code ${ref()}} injects real table identifiers, and a schema cache so that columns resolve.
 * Without the compiled graph the injector falls back to {@code NULL} and nothing resolves.
 */
public abstract class DataformProjectFixture extends BasePlatformTestCase {

    protected static final String FIXTURES =
            "src/test/resources/projects/user_purchase/definitions/";

    private static final String[] ACTIONS = {
            "bronze_orders", "silver_orders", "gold_customer_ltv",
            "gold_customer_purchase_summary", "silver_customers", "bronze_customers"
    };

    /**
     * The compiled query of each action. Column lineage is extracted from these, so they carry the
     * same column flow as the fixtures: silver reads bronze, gold reads silver.
     */
    private static String queryOf(String action) {
        return switch (action) {
            case "bronze_orders" -> "SELECT order_id, customer_id, order_ts, 'PAID' AS order_status "
                    + "FROM UNNEST([STRUCT(1 AS order_id, 1 AS customer_id, "
                    + "CURRENT_TIMESTAMP() AS order_ts)])";
            case "silver_orders" -> "SELECT order_id, customer_id, order_ts, order_status "
                    + "FROM `proj.ds.bronze_orders` bo";
            case "gold_customer_ltv" -> "WITH customer_orders AS ("
                    + "SELECT o.customer_id, o.order_id, SUM(s.order_amount) AS lifetime_value "
                    + "FROM `proj.ds.silver_orders` AS o "
                    + "INNER JOIN `proj.ds.gold_customer_purchase_summary` AS s "
                    + "ON o.order_id = s.order_id GROUP BY o.customer_id) "
                    + "SELECT c.customer_id, co.order_id, co.lifetime_value "
                    + "FROM `proj.ds.silver_customers` AS c "
                    + "LEFT JOIN customer_orders AS co ON c.customer_id = co.customer_id";
            case "bronze_customers" -> "SELECT * FROM UNNEST([STRUCT(1 AS customer_id, "
                    + "'a' AS full_name, 'b' AS email)])";
            case "gold_customer_purchase_summary" ->
                    "SELECT order_id, 1 AS order_amount FROM `proj.ds.silver_orders`";
            default -> "SELECT customer_id, 'a' AS full_name, 'b' AS email";
        };
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        installCompiledGraph();
        installSchema();
    }

    private static void set(Object target, String field, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new IllegalStateException("set " + field, e);
        }
    }

    private void installCompiledGraph() {
        List<CompiledTable> tables = new ArrayList<>();
        for (String name : ACTIONS) {
            Target target = new Target();
            set(target, "database", "proj");
            set(target, "schema", "ds");
            set(target, "name", name);
            CompiledTable table = new CompiledTable();
            set(table, "type", "table");
            set(table, "target", target);
            set(table, "query", queryOf(name));
            set(table, "fileName", "definitions/" + name + ".sqlx");
            set(table, "tags", List.of());
            set(table, "dependencyTargets", List.of());
            tables.add(table);
        }
        CompiledGraph graph = new CompiledGraph();
        set(graph, "tables", tables);
        set(graph, "declarations", List.of());
        set(graph, "operations", List.of());
        set(graph, "assertions", List.of());
        set(getProject().getService(DataformCompilationService.class), "compiledGraph", graph);
    }

    private void installSchema() {
        String json = "{"
                + entry("bronze_orders", "order_id", "customer_id", "order_ts", "order_status")
                + "," + entry("silver_orders", "order_id", "customer_id", "order_ts", "order_status")
                + "," + entry("gold_customer_ltv", "customer_id", "order_id", "lifetime_value")
                + "," + entry("gold_customer_purchase_summary", "order_id", "order_amount")
                + "," + entry("silver_customers", "customer_id", "full_name", "email")
                + "," + entry("bronze_customers", "customer_id", "full_name", "email")
                + "}";
        DataformTableSchemaService.State state = new DataformTableSchemaService.State();
        state.schemaCacheJson = json;
        DataformTableSchemaService.getInstance(getProject()).loadState(state);
    }

    private String entry(String name, String... columns) {
        StringBuilder cols = new StringBuilder();
        for (String c : columns) {
            if (!cols.isEmpty()) cols.append(",");
            cols.append("{\"name\":\"").append(c)
                    .append("\",\"type\":\"STRING\",\"mode\":\"NULLABLE\",\"subFields\":[]}");
        }
        return "\"proj.ds." + name + "\":{\"columns\":[" + cols
                + "],\"lastModified\":0,\"fileName\":\"definitions/" + name + ".sqlx\"}";
    }

    protected PsiFile open(String path) throws Exception {
        String text = Files.readString(Path.of(FIXTURES + path));
        String name = path.substring(path.lastIndexOf('/') + 1);
        PsiFile file = myFixture.addFileToProject("definitions/" + name, text);
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        return myFixture.getFile();
    }

    /** The injected SQL element at a token, as the SQL plugin sees it. */
    protected PsiElement injectedAt(PsiFile file, int line, String token) {
        Document document = PsiDocumentManager.getInstance(getProject()).getDocument(file);
        int lineStart = document.getLineStartOffset(line - 1);
        String lineText = document.getText()
                .substring(lineStart, document.getLineEndOffset(line - 1));
        int column = lineText.indexOf(token);
        assertTrue("token '" + token + "' must be on line " + line, column >= 0);
        PsiElement injected = InjectedLanguageManager.getInstance(getProject())
                .findInjectedElementAt(file, lineStart + column + token.length() - 1);
        assertNotNull("token '" + token + "' must be inside the injected SQL", injected);
        return injected;
    }

    /**
     * The reference a token belongs to, which is the element the resolve extension is handed. The
     * token itself is a leaf inside an identifier inside the reference.
     */
    protected PsiElement selectItemAt(PsiFile file, int line, String token) {
        PsiElement item = injectedAt(file, line, token);
        while (item.getParent() != null && isPartOfReference(item.getParent())) {
            item = item.getParent();
        }
        return item;
    }

    private static boolean isPartOfReference(PsiElement element) {
        IElementType type = element.getNode().getElementType();
        return type == SqlCompositeElementTypes.SQL_IDENTIFIER
                || type == SqlCompositeElementTypes.SQL_COLUMN_REFERENCE;
    }

    /** The one-based line an element sits on in its host file. */
    protected int lineOf(PsiElement element) {
        assertNotNull("element must not be null", element);
        InjectedLanguageManager manager = InjectedLanguageManager.getInstance(getProject());
        PsiFile host = manager.getTopLevelFile(element.getContainingFile());
        Document document = PsiDocumentManager.getInstance(getProject()).getDocument(host);
        return document.getLineNumber(manager.injectedToHost(element, element.getTextOffset())) + 1;
    }
}
