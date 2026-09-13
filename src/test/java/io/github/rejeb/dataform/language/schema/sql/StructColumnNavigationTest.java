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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.compilation.DataformCompilationService;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.compilation.model.CompiledTable;
import io.github.rejeb.dataform.language.compilation.model.Target;
import io.github.rejeb.dataform.language.schema.sql.model.StructColumnPath;
import io.github.rejeb.dataform.language.schema.sql.usages.ColumnUsageRow;
import io.github.rejeb.dataform.language.schema.sql.usages.ColumnUsageRows;
import io.github.rejeb.dataform.language.schema.sql.usages.ColumnWindowTarget;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Navigating a struct column and the fields inside it, at every depth.
 *
 * <p>The schema holds a struct column, never its fields, and the platform resolves a field to an
 * element of the column's type that lives in a throwaway file of its own. A reader pointing at
 * {@code n.customer.origin.code} therefore has to be answered by walking the path rather than by
 * resolving it, and the answer has to say both where the field is written and who else reads it.</p>
 */
public class StructColumnNavigationTest extends BasePlatformTestCase {

    private static void set(Object target, String field, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new IllegalStateException("set " + field, e);
        }
    }

    private static CompiledTable action(String name) {
        Target target = new Target();
        set(target, "database", "proj");
        set(target, "schema", "gold");
        set(target, "name", name);
        CompiledTable table = new CompiledTable();
        set(table, "type", "table");
        set(table, "target", target);
        set(table, "query", "SELECT 1");
        set(table, "fileName", "definitions/" + name + ".sqlx");
        set(table, "tags", List.of());
        set(table, "dependencyTargets", List.of());
        return table;
    }

    private static final String NESTED_SCHEMA =
            "\"proj.gold.nested_orders\":{\"columns\":["
                    + "{\"name\":\"order_id\",\"type\":\"INT64\",\"mode\":\"NULLABLE\",\"subFields\":[]},"
                    + "{\"name\":\"customer\",\"type\":\"STRUCT\",\"mode\":\"NULLABLE\",\"subFields\":["
                    + "{\"name\":\"name\",\"type\":\"STRING\",\"mode\":\"NULLABLE\",\"subFields\":[]},"
                    + "{\"name\":\"email\",\"type\":\"STRING\",\"mode\":\"NULLABLE\",\"subFields\":[]},"
                    + "{\"name\":\"origin\",\"type\":\"STRUCT\",\"mode\":\"NULLABLE\",\"subFields\":["
                    + "{\"name\":\"code\",\"type\":\"STRING\",\"mode\":\"NULLABLE\",\"subFields\":[]}]}]},"
                    + "{\"name\":\"items\",\"type\":\"STRUCT\",\"mode\":\"REPEATED\",\"subFields\":["
                    + "{\"name\":\"code\",\"type\":\"STRING\",\"mode\":\"NULLABLE\",\"subFields\":[]}]}],"
                    + "\"lastModified\":0,\"fileName\":\"definitions/nested_orders.sqlx\"}";

    private static final String CUSTOMERS_SCHEMA =
            "\"proj.gold.customers\":{\"columns\":["
                    + "{\"name\":\"full_name\",\"type\":\"STRING\",\"mode\":\"NULLABLE\",\"subFields\":[]},"
                    + "{\"name\":\"email\",\"type\":\"STRING\",\"mode\":\"NULLABLE\",\"subFields\":[]},"
                    + "{\"name\":\"country_code\",\"type\":\"STRING\",\"mode\":\"NULLABLE\",\"subFields\":[]}],"
                    + "\"lastModified\":0,\"fileName\":\"definitions/customers.sqlx\"}";

    private PsiFile producingFile;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        CompiledGraph graph = new CompiledGraph();
        set(graph, "tables", List.of(action("nested_orders"), action("flat_orders"),
                action("other_reader"), action("customers")));
        set(graph, "declarations", List.of());
        set(graph, "operations", List.of());
        set(graph, "assertions", List.of());
        set(getProject().getService(DataformCompilationService.class), "compiledGraph", graph);

        DataformTableSchemaService.State state = new DataformTableSchemaService.State();
        state.schemaCacheJson = "{" + NESTED_SCHEMA + "," + CUSTOMERS_SCHEMA + "}";
        DataformTableSchemaService.getInstance(getProject()).loadState(state);

        myFixture.addFileToProject("definitions/customers.sqlx",
                "config { type: \"view\", schema: \"gold\" }\n\n"
                        + "SELECT\n"
                        + "  full_name AS full_name,\n"
                        + "  email AS email,\n"
                        + "  country_code AS country_code\n"
                        + "FROM `proj.gold.raw`\n");
        producingFile = producer();
    }

    /** The action building the struct column, which is where every field of it is written. */
    private PsiFile producer() {
        return myFixture.addFileToProject("definitions/nested_orders.sqlx",
                "config { type: \"table\", schema: \"gold\" }\n\n"
                        + "SELECT\n"
                        + "  o.order_id AS order_id,\n"
                        + "  STRUCT(\n"
                        + "    c.full_name AS name,\n"
                        + "    c.email AS email,\n"
                        + "    STRUCT(\n"
                        + "      c.country_code AS code\n"
                        + "    ) AS origin\n"
                        + "  ) AS customer,\n"
                        + "  ARRAY_AGG(STRUCT(i.code AS code)) AS items\n"
                        + "FROM `proj.gold.source` AS o JOIN `proj.gold.customers` AS c ON TRUE\n");
    }

    private PsiFile reader(String name, String body) {
        PsiFile file = myFixture.addFileToProject("definitions/" + name + ".sqlx",
                "config { type: \"view\", schema: \"gold\" }\n\n" + body);
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        return myFixture.getFile();
    }

    private static final String READER_BODY =
            "SELECT\n"
                    + "  n.order_id AS order_id,\n"
                    + "  n.customer.name AS customer_name,\n"
                    + "  n.customer.email AS customer_email,\n"
                    + "  n.customer.origin.code AS country_code,\n"
                    + "  n.customer AS whole_customer\n"
                    + "FROM ${ref(\"nested_orders\")} AS n\n";

    private PsiElement tokenAt(PsiFile file, String occurrence, String token) {
        int at = file.getText().indexOf(occurrence);
        assertTrue("'" + occurrence + "' must be present", at >= 0);
        int offset = at + occurrence.indexOf(token) + token.length() / 2;
        PsiElement injected = InjectedLanguageManager.getInstance(getProject())
                .findInjectedElementAt(file, offset);
        assertNotNull("the offset must be inside the injected SQL", injected);
        return injected;
    }

    private ColumnWindowTarget targetAt(PsiFile file, String occurrence, String token) {
        int at = file.getText().indexOf(occurrence);
        assertTrue("'" + occurrence + "' must be present", at >= 0);
        return ColumnWindowTarget.at(file, at + occurrence.indexOf(token) + token.length() / 2);
    }

    private StructColumnPath pathAt(PsiFile file, String occurrence, String token) {
        return StructColumnPathResolver.getInstance(getProject())
                .pathAt(tokenAt(file, occurrence, token));
    }

    // ---------- phase 1: the path ----------

    public void testALeafOfThePathIsRead() {
        PsiFile file = reader("flat_orders", READER_BODY);
        StructColumnPath path = pathAt(file, "n.customer.name AS", "name AS");
        assertNotNull("a field of a struct column must be recognised", path);
        assertEquals("customer.name", path.dottedName());
        assertEquals("name", path.leafName());
        assertTrue(path.isField());
    }

    public void testADeeperLeafKeepsEverySegment() {
        PsiFile file = reader("flat_orders", READER_BODY);
        StructColumnPath path = pathAt(file, "n.customer.origin.code AS", "code AS");
        assertNotNull(path);
        assertEquals("customer.origin.code", path.dottedName());
        assertEquals("code", path.leafName());
    }

    /** A middle segment names the struct it is, not the leaf below it. */
    public void testAMiddleSegmentStopsWhereTheCaretIs() {
        PsiFile file = reader("flat_orders", READER_BODY);
        StructColumnPath path = pathAt(file, "n.customer.origin.code AS", "origin.");
        assertNotNull("a struct inside a struct must be recognised", path);
        assertEquals("customer.origin", path.dottedName());
    }

    public void testTheStructColumnItselfIsAPathOfNoFields() {
        PsiFile file = reader("flat_orders", READER_BODY);
        StructColumnPath path = pathAt(file, "n.customer.name AS", "customer.");
        assertNotNull(path);
        assertEquals("customer", path.dottedName());
        assertFalse("the column is not a field of itself", path.isField());
    }

    /** The table qualifier is not a column, and must not be taken for one. */
    public void testATableAliasIsNotAPath() {
        PsiFile file = reader("flat_orders", READER_BODY);
        assertNull(pathAt(file, "n.customer.name AS", "n"));
    }

    public void testAFieldTheSchemaDoesNotHoldIsNotAPath() {
        PsiFile file = reader("flat_orders",
                "SELECT n.customer.nickname AS x FROM ${ref(\"nested_orders\")} AS n\n");
        assertNull("the schema holds no such field, so there is nothing to navigate to",
                pathAt(file, "n.customer.nickname AS", "nickname"));
    }

    // ---------- phase 2: the declaration ----------

    private String declarationLineOf(ColumnWindowTarget target) {
        assertNotNull("the caret must land on a column", target);
        assertEquals("exactly one declaration is expected, got " + target.declarations(),
                1, target.declarations().size());
        PsiElement declaring = target.declarations().getFirst();
        InjectedLanguageManager manager = InjectedLanguageManager.getInstance(getProject());
        PsiFile host = manager.getTopLevelFile(declaring.getContainingFile());
        int offset = manager.injectedToHost(declaring, declaring.getTextOffset());
        return host.getName() + ":" + (com.intellij.psi.PsiDocumentManager.getInstance(getProject())
                .getDocument(host).getLineNumber(offset) + 1) + " " + declaring.getText();
    }

    public void testALeafIsDeclaredByItsFieldOfTheConstructor() {
        PsiFile file = reader("flat_orders", READER_BODY);
        assertEquals("nested_orders.sqlx:6 name",
                declarationLineOf(targetAt(file, "n.customer.name AS", "name AS")));
    }

    public void testADeeperLeafIsDeclaredByTheInnerConstructor() {
        PsiFile file = reader("flat_orders", READER_BODY);
        assertEquals("nested_orders.sqlx:9 code",
                declarationLineOf(targetAt(file, "n.customer.origin.code AS", "code AS")));
    }

    public void testAMiddleSegmentIsDeclaredByTheFieldHoldingIt() {
        PsiFile file = reader("flat_orders", READER_BODY);
        assertEquals("nested_orders.sqlx:10 origin",
                declarationLineOf(targetAt(file, "n.customer.origin.code AS", "origin.")));
    }

    public void testTheStructColumnIsDeclaredByItsSelectItem() {
        PsiFile file = reader("flat_orders", READER_BODY);
        assertEquals("nested_orders.sqlx:11 customer",
                declarationLineOf(targetAt(file, "n.customer.name AS", "customer.")));
    }

    /** A field of a struct collected by an aggregate is written in a constructor just the same. */
    public void testAFieldInsideAnAggregatedStructIsFound() {
        PsiFile file = reader("flat_orders",
                "SELECT i.code AS item_code\n"
                        + "FROM ${ref(\"nested_orders\")} AS n,\n"
                        + "UNNEST(n.items) AS i\n");
        PsiElement declaring = StructFieldDeclarationLocator.declaringElement(
                producingFile, List.of("items", "code"));
        assertNotNull(declaring);
        assertEquals("code", declaring.getText());
        assertEquals("the field of the aggregated struct, not the one of customer.origin",
                12, lineOf(declaring));
    }

    private int lineOf(PsiElement element) {
        InjectedLanguageManager manager = InjectedLanguageManager.getInstance(getProject());
        PsiFile host = manager.getTopLevelFile(element.getContainingFile());
        int offset = manager.injectedToHost(element, element.getTextOffset());
        return com.intellij.psi.PsiDocumentManager.getInstance(getProject())
                .getDocument(host).getLineNumber(offset) + 1;
    }

    // ---------- phase 3: the usages ----------

    private List<String> rowsOf(ColumnWindowTarget target) {
        assertNotNull(target);
        List<String> out = new ArrayList<>();
        for (ColumnUsageRow row : ColumnUsageRows.of(getProject(), target, null)) {
            out.add(row.isHeading()
                    ? "== " + row.heading() + " " + row.count()
                    : row.kind() + " " + row.location());
        }
        return out;
    }

    public void testAFieldListsTheFilesReadingIt() {
        reader("other_reader",
                "SELECT n.customer.origin.code AS code_again\n"
                        + "FROM ${ref(\"nested_orders\")} AS n\n");
        PsiFile file = reader("flat_orders", READER_BODY);
        List<String> rows = rowsOf(targetAt(file, "n.customer.origin.code AS", "code AS"));
        assertTrue("the other file reads the same field and must be listed, got " + rows,
                rows.contains("USAGE other_reader.sqlx:3"));
        assertTrue("the field must carry a declaration too, got " + rows,
                rows.contains("DECLARATION nested_orders.sqlx:9"));
    }

    /** A field of another column sharing the leaf name is a different field. */
    public void testAFieldOfAnotherColumnIsNotListed() {
        reader("other_reader",
                "SELECT i.code AS item_code\n"
                        + "FROM ${ref(\"nested_orders\")} AS n,\n"
                        + "UNNEST(n.items) AS i\n");
        PsiFile file = reader("flat_orders", READER_BODY);
        List<String> rows = rowsOf(targetAt(file, "n.customer.origin.code AS", "code AS"));
        assertFalse("items.code is not customer.origin.code, got " + rows,
                rows.contains("USAGE other_reader.sqlx:1"));
    }

    /** The place the window was opened on helps nobody, so it is never one of its own rows. */
    public void testTheClickedOccurrenceIsNotListed() {
        PsiFile file = reader("flat_orders", READER_BODY);
        List<String> rows = rowsOf(targetAt(file, "n.customer.origin.code AS", "code AS"));
        assertFalse("the row the caret sits on must not be listed, got " + rows,
                rows.contains("USAGE flat_orders.sqlx:6"));
    }

    // ---------- the alias over a nested field ----------

    /**
     * An alias names a column of this table, and what that column is built from is the field the
     * renamed expression reads. The platform resolves that read into the column's type rather than
     * into a file, so without the path being walked the alias had a declaration nobody could open:
     * the row was dropped and the window came up empty, which reads as a column resolving to nothing.
     */
    public void testAnAliasOverANestedFieldIsDeclaredByThatField() {
        PsiFile file = reader("flat_orders", READER_BODY);
        assertEquals("nested_orders.sqlx:6 name",
                declarationLineOf(targetAt(file, "n.customer.name AS customer_name", "customer_name")));
    }

    public void testAnAliasOverADeeperFieldIsDeclaredByIt() {
        PsiFile file = reader("flat_orders", READER_BODY);
        assertEquals("nested_orders.sqlx:9 code",
                declarationLineOf(targetAt(file, "origin.code AS country_code", "country_code")));
    }

    public void testAnAliasOverTheWholeStructIsDeclaredByTheColumn() {
        PsiFile file = reader("flat_orders", READER_BODY);
        assertEquals("nested_orders.sqlx:11 customer",
                declarationLineOf(targetAt(file, "n.customer AS whole_customer", "whole_customer")));
    }

    /** A window with nothing to show never opens, so the rows of an alias must not be empty. */
    public void testTheWindowOfAnAliasOverANestedFieldIsNotEmpty() {
        PsiFile file = reader("flat_orders", READER_BODY);
        List<String> rows = rowsOf(targetAt(file, "n.customer.name AS customer_name", "customer_name"));
        assertFalse("an empty window never opens, which is the symptom being fixed", rows.isEmpty());
        assertTrue("the field it is built from must be named, got " + rows,
                rows.contains("DECLARATION nested_orders.sqlx:6"));
    }

    // ---------- phase 5: fields read off an UNNEST alias ----------

    private static final String UNNEST_BODY =
            "SELECT\n"
                    + "  n.order_id AS order_id,\n"
                    + "  i.code AS item_code\n"
                    + "FROM ${ref(\"nested_orders\")} AS n,\n"
                    + "UNNEST(n.items) AS i\n";

    /**
     * An UNNEST alias resolves to nothing at all, so the array it unnests is found where the alias is
     * written rather than by resolving it.
     */
    public void testAFieldReadOffAnUnnestAliasNamesTheArrayColumn() {
        PsiFile file = reader("flat_orders", UNNEST_BODY);
        StructColumnPath path = pathAt(file, "i.code AS item_code", "code AS");
        assertNotNull("a field of an unnested array must be recognised", path);
        assertEquals("items.code", path.dottedName());
    }

    public void testAFieldReadOffAnUnnestAliasIsDeclaredInsideTheAggregate() {
        PsiFile file = reader("flat_orders", UNNEST_BODY);
        assertEquals("nested_orders.sqlx:12 code",
                declarationLineOf(targetAt(file, "i.code AS item_code", "code AS")));
    }

    /** The alias itself names the array, not a field of it. */
    public void testTheUnnestAliasNamesTheArrayItself() {
        PsiFile file = reader("flat_orders", UNNEST_BODY);
        StructColumnPath path = pathAt(file, "i.code AS item_code", "i");
        assertNotNull(path);
        assertEquals("items", path.dottedName());
        assertFalse(path.isField());
    }

    public void testAnUnknownAliasIsNotAPath() {
        PsiFile file = reader("flat_orders",
                "SELECT unknown.code AS c FROM ${ref(\"nested_orders\")} AS n\n");
        assertNull(pathAt(file, "unknown.code AS", "code AS"));
    }

    /** The same field read through an UNNEST in another file is the same field. */
    public void testAnUnnestedFieldListsReadsFromOtherFiles() {
        reader("other_reader",
                "SELECT j.code AS code_again\n"
                        + "FROM ${ref(\"nested_orders\")} AS m,\n"
                        + "UNNEST(m.items) AS j\n");
        PsiFile file = reader("flat_orders", UNNEST_BODY);
        List<String> rows = rowsOf(targetAt(file, "i.code AS item_code", "code AS"));
        assertTrue("the same field unnested under another alias must be listed, got " + rows,
                rows.contains("USAGE other_reader.sqlx:3"));
    }

    /** items.code and customer.origin.code share a leaf name and are still different fields. */
    public void testAnUnnestedFieldIsNotConfusedWithASameNamedFieldOfAnotherColumn() {
        reader("other_reader",
                "SELECT n.customer.origin.code AS country_code\n"
                        + "FROM ${ref(\"nested_orders\")} AS n\n");
        PsiFile file = reader("flat_orders", UNNEST_BODY);
        List<String> rows = rowsOf(targetAt(file, "i.code AS item_code", "code AS"));
        assertFalse("customer.origin.code is not items.code, got " + rows,
                rows.contains("USAGE other_reader.sqlx:3"));
    }

    // ---------- phase 6: a struct lists the fields it holds ----------

    /**
     * A query cannot read a struct on its own, so what explains it is what it can read: every leaf
     * inside it, at any depth, each in the file writing it.
     */
    public void testAStructColumnListsItsLeavesRecursively() {
        PsiFile file = reader("flat_orders", READER_BODY);
        List<String> rows = rowsOf(targetAt(file, "n.customer.name AS", "customer."));
        assertTrue("the heading must lead the group, got " + rows,
                rows.contains("== FIELDS 3"));
        assertTrue("a field of the struct, got " + rows,
                rows.contains("FIELD nested_orders.sqlx:6"));
        assertTrue("another field of it, got " + rows,
                rows.contains("FIELD nested_orders.sqlx:7"));
        assertTrue("a leaf nested one level deeper, got " + rows,
                rows.contains("FIELD nested_orders.sqlx:9"));
        assertFalse("a field holding fields is explained by its leaves, not by itself, got " + rows,
                rows.contains("FIELD nested_orders.sqlx:10"));
    }

    public void testAStructInsideAStructListsOnlyItsOwnLeaves() {
        PsiFile file = reader("flat_orders", READER_BODY);
        List<String> rows = rowsOf(targetAt(file, "n.customer.origin.code AS", "origin."));
        assertTrue("only the leaf it holds, got " + rows, rows.contains("== FIELDS 1"));
        assertTrue(rows.contains("FIELD nested_orders.sqlx:9"));
        assertFalse("the fields of its parent are not its own, got " + rows,
                rows.contains("FIELD nested_orders.sqlx:6"));
    }

    /** A leaf holds no fields, so it lists none. */
    public void testALeafListsNoFields() {
        PsiFile file = reader("flat_orders", READER_BODY);
        List<String> rows = rowsOf(targetAt(file, "n.customer.name AS", "name AS"));
        assertFalse("a leaf has nothing inside it, got " + rows,
                rows.stream().anyMatch(row -> row.startsWith("== FIELDS")));
    }

    /** An unnested array is a struct too, so the fields of its element are listed. */
    public void testAnUnnestedArrayListsTheFieldsOfItsElement() {
        PsiFile file = reader("flat_orders", UNNEST_BODY);
        List<String> rows = rowsOf(targetAt(file, "i.code AS item_code", "i"));
        assertTrue("the element of the array holds one field, got " + rows,
                rows.contains("== FIELDS 1"));
        assertTrue(rows.contains("FIELD nested_orders.sqlx:12"));
    }

    // ---------- phase 4: Find Usages ----------

    private com.intellij.usages.UsageTarget[] findUsagesTargetsAt(PsiFile file, String occurrence,
                                                                  String token) {
        int at = file.getText().indexOf(occurrence);
        assertTrue("'" + occurrence + "' must be present", at >= 0);
        myFixture.getEditor().getCaretModel()
                .moveToOffset(at + occurrence.indexOf(token) + token.length() / 2);
        return new io.github.rejeb.dataform.language.schema.sql.usages
                .StructFieldUsageTargetProvider().getTargets(myFixture.getEditor(), file);
    }

    /**
     * Find Usages starts from what the caret resolves to, and a field resolves to an element of its
     * column's type that every struct of the same shape shares. A target of our own is offered so the
     * Find window searches the field rather than that shared element.
     */
    public void testFindUsagesIsOfferedATargetOnAField() {
        PsiFile file = reader("flat_orders", READER_BODY);
        com.intellij.usages.UsageTarget[] targets =
                findUsagesTargetsAt(file, "n.customer.origin.code AS", "code AS");
        assertNotNull("a field must be searchable from Find Usages", targets);
        assertEquals(1, targets.length);
        assertEquals("customer.origin.code", targets[0].getName());
        assertTrue("the target must lead back to where the field is written",
                targets[0].canNavigate());
        assertTrue(targets[0].isValid());
    }

    /** A column of a table is the platform's business, so nothing of ours is offered for it. */
    public void testFindUsagesIsLeftAloneOnAPlainColumn() {
        PsiFile file = reader("flat_orders", READER_BODY);
        assertNull("a plain column must keep the platform's own handling",
                findUsagesTargetsAt(file, "n.order_id AS order_id", "order_id AS"));
    }

    /** A struct column is not a field of anything, so it keeps the platform's handling too. */
    public void testFindUsagesIsLeftAloneOnTheStructColumnItself() {
        PsiFile file = reader("flat_orders", READER_BODY);
        assertNull(findUsagesTargetsAt(file, "n.customer.name AS", "customer."));
    }

    public void testFindUsagesIsOfferedATargetOnAnUnnestedField() {
        PsiFile file = reader("flat_orders", UNNEST_BODY);
        com.intellij.usages.UsageTarget[] targets =
                findUsagesTargetsAt(file, "i.code AS item_code", "code AS");
        assertNotNull(targets);
        assertEquals("items.code", targets[0].getName());
    }

    // ---------- the declaration of a field, clicked in the file writing it ----------

    /** Reading the producing file, with the caret on the alias naming a field of the struct. */
    private ColumnWindowTarget producerTargetAt(String occurrence, String token) {
        myFixture.configureFromExistingVirtualFile(producingFile.getVirtualFile());
        PsiFile file = myFixture.getFile();
        int at = file.getText().indexOf(occurrence);
        assertTrue("'" + occurrence + "' must be present", at >= 0);
        return ColumnWindowTarget.at(file, at + occurrence.indexOf(token) + token.length() / 2);
    }

    /**
     * A field is written in the action building its column, and nothing references that alias, so a
     * search over references answers nothing for it. Asked from the declaring end the question is the
     * same as from a read, so the path is carried here too and the reads are found.
     */
    public void testTheAliasOfAFieldListsTheReadsOfThatField() {
        reader("flat_orders", READER_BODY);
        ColumnWindowTarget target = producerTargetAt("AS name,", "name");
        assertNotNull("the alias of a field must open a window", target);
        assertNotNull("it names a field of a struct column", target.structPath());
        assertEquals("customer.name", target.structPath().dottedName());

        List<String> rows = rowsOf(target);
        assertTrue("the file reading the field must be listed, got " + rows,
                rows.contains("USAGE flat_orders.sqlx:5"));
    }

    /** The declaration it already showed must survive: the field is built from another column. */
    public void testTheAliasOfAFieldStillNamesWhatItIsBuiltFrom() {
        reader("flat_orders", READER_BODY);
        List<String> rows = rowsOf(producerTargetAt("AS name,", "name"));
        assertTrue("what the field reads must still be named, got " + rows,
                rows.contains("DECLARATION customers.sqlx:4"));
        assertTrue("and the reads of the field must be listed beside it, got " + rows,
                rows.contains("USAGE flat_orders.sqlx:5"));
    }

    /** A field holding fields lists them from the declaring end too. */
    public void testTheAliasOfANestedStructListsItsLeaves() {
        List<String> rows = rowsOf(producerTargetAt(") AS origin", "origin"));
        assertTrue("got " + rows, rows.contains("== FIELDS 1"));
        assertTrue("got " + rows, rows.contains("FIELD nested_orders.sqlx:9"));
    }

    /** A column of the table is not a field of anything, so it keeps the search it always had. */
    public void testTheAliasOfAColumnCarriesNoFieldPath() {
        ColumnWindowTarget target = producerTargetAt(") AS customer", "customer");
        assertNotNull(target);
        assertNull("a column the schema holds is not a field of one", target.structPath());
    }
}
