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
package io.github.rejeb.dataform.language.schema.sql.usages;

import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import io.github.rejeb.dataform.language.evaluation.DataformExpressionEvaluationService;
import io.github.rejeb.dataform.language.evaluation.DataformExpressionEvaluationServiceImpl;
import io.github.rejeb.dataform.language.injection.SqlxInjectionRefresher;
import io.github.rejeb.dataform.language.lineage.column.ColumnRef;
import io.github.rejeb.dataform.language.schema.sql.ColumnOriginService;
import io.github.rejeb.dataform.language.schema.sql.DataformProjectFixture;
import io.github.rejeb.dataform.language.schema.sql.model.DataformDasColumn;
import io.github.rejeb.dataform.language.schema.sql.SqlxColumnAtCaret;

import java.util.ArrayList;
import java.util.List;

/**
 * The window a Ctrl+Click on a column opens: a Declaration group and a Usages group, each row
 * naming the file and line it sits in rather than the project it belongs to.
 */
public class ColumnUsagesWindowTest extends DataformProjectFixture {

    private List<PsiFile> openAll() throws Exception {
        return List.of(open("bronze/bronze_orders.sqlx"),
                open("silver/silver_orders.sqlx"),
                open("gold/gold_customer_ltv.sqlx"));
    }

    private List<ColumnUsageRow> rowsAt(PsiFile file, int line, String token) {
        PsiElement injected = injectedAt(file, line, token);
        int offset = InjectedLanguageManager.getInstance(getProject())
                .injectedToHost(injected, injected.getTextOffset());
        ColumnWindowTarget target = ColumnWindowTarget.at(file, offset);
        assertNotNull("the caret must land on a column", target);
        return ColumnUsageRows.of(getProject(), target, SqlxColumnAtCaret.referenceOf(injected));
    }

    private List<String> describe(List<ColumnUsageRow> rows) {
        List<String> out = new ArrayList<>();
        for (ColumnUsageRow row : rows) {
            out.add(row.isHeading()
                    ? "== " + row.heading() + " " + row.count()
                    : row.kind() + " " + row.location());
        }
        return out;
    }

    public void testTheWindowIsGroupedIntoDeclarationAndUsages() throws Exception {
        List<String> rows = describe(rowsAt(openAll().get(1), 23, "order_id"));
        assertTrue("a Declaration heading must lead its group, got " + rows,
                rows.stream().anyMatch(r -> r.startsWith("== " + ColumnUsageRows.DECLARATION)));
        assertTrue("a Usages heading must lead its group, got " + rows,
                rows.stream().anyMatch(r -> r.startsWith("== " + ColumnUsageRows.USAGES)));
    }

    public void testEveryRowNamesItsFileAndLine() throws Exception {
        for (ColumnUsageRow row : rowsAt(openAll().get(1), 23, "order_id")) {
            if (row.isHeading()) continue;
            assertTrue("a row must name its file and line, got '" + row.location() + "'",
                    row.location().matches("[\\w.]+\\.sqlx:\\d+"));
        }
    }

    /** The upstream row is the column this one is read from, in the file that declares it. */
    public void testDeclarationIsTheColumnItIsBuiltFrom() throws Exception {
        List<String> rows = describe(rowsAt(openAll().get(1), 23, "order_id"));
        assertTrue("silver is built from bronze's column, so bronze is its declaration, got " + rows,
                rows.contains("DECLARATION bronze_orders.sqlx:23"));
    }

    public void testEverythingTheSearchFindsIsAUsage() throws Exception {
        List<String> rows = describe(rowsAt(openAll().get(1), 23, "order_id"));
        assertFalse("a place that reads the column is a usage of it, never a declaration, got " + rows,
                rows.contains("DECLARATION gold_customer_ltv.sqlx:31"));
        assertTrue("it belongs under Usages, got " + rows,
                rows.contains("USAGE gold_customer_ltv.sqlx:31"));
    }

    public void testTheReadsOfTheColumnAreListed() throws Exception {
        List<String> rows = describe(rowsAt(openAll().get(1), 23, "order_id"));
        assertTrue("gold reads silver's column and must be listed, got " + rows,
                rows.contains("USAGE gold_customer_ltv.sqlx:17"));
    }

    public void testTheClickedOccurrenceIsNotListed() throws Exception {
        List<String> rows = describe(rowsAt(openAll().get(1), 23, "order_id"));
        assertFalse("the row the user clicked helps nobody, got " + rows,
                rows.contains("DECLARATION silver_orders.sqlx:23"));
    }

    public void testARowCarriesTheExpressionItReads() throws Exception {
        List<ColumnUsageRow> rows = rowsAt(openAll().get(1), 23, "order_id");
        ColumnUsageRow read = rows.stream()
                .filter(r -> !r.isHeading() && r.location().startsWith("gold_customer_ltv"))
                .findFirst().orElse(null);
        assertNotNull("a read of the column must be present", read);
        assertEquals("the column name is drawn apart from the rest", "order_id", read.name());
        assertFalse("the expression around it is kept", read.before().isEmpty());
    }

    public void testARowOpensTheHostFileAtTheLineItNames() throws Exception {
        List<ColumnUsageRow> rows = rowsAt(openAll().get(1), 23, "order_id");
        for (ColumnUsageRow row : rows) {
            if (row.isHeading()) continue;
            OpenFileDescriptor target = row.target();
            assertNotNull("a row must point at a place to open", target);
            assertFalse("a row opening an injected file makes the editor validate the injection"
                            + " on the event thread, outside a read action: " + row.location(),
                    target.getFile() instanceof VirtualFileWindow);

            Document document = FileDocumentManager.getInstance().getDocument(target.getFile());
            assertNotNull("the file a row opens must have a document", document);
            assertEquals("a row opens the line it names",
                    row.location(),
                    target.getFile().getName() + ":"
                            + (document.getLineNumber(target.getOffset()) + 1));
        }
    }

    public void testHeadingsAreUppercase() throws Exception {
        for (ColumnUsageRow row : rowsAt(openAll().get(1), 23, "order_id")) {
            if (!row.isHeading()) continue;
            assertEquals("a heading is shown in upper case",
                    row.heading().toUpperCase(java.util.Locale.ROOT), row.heading());
        }
    }

    public void testEveryEntryKnowsTheHeadingItFoldsUnder() throws Exception {
        for (ColumnUsageRow row : rowsAt(openAll().get(1), 23, "order_id")) {
            if (row.isHeading()) continue;
            assertTrue("an entry must name its group so folding can hide it, got " + row.group(),
                    row.group().equals(ColumnUsageRows.DECLARATION)
                            || row.group().equals(ColumnUsageRows.USAGES));
        }
    }

    /** A column a query names for itself, as {@code order_id as country} inside a CTE. */
    private PsiFile marts(String body) throws Exception {
        PsiFile file = myFixture.addFileToProject("definitions/marts.sqlx",
                "config { type: \"table\" }\n" + body);
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        return myFixture.getFile();
    }

    private ColumnWindowTarget targetOn(PsiFile file, String token, String inside) {
        int offset = file.getText().indexOf(inside) + inside.indexOf(token) + 1;
        ColumnWindowTarget target = ColumnWindowTarget.at(file, offset);
        assertNotNull("the caret must land on a column", target);
        return target;
    }

    public void testAnAliasOpensTheWindowToo() throws Exception {
        PsiFile file = marts("WITH stats AS (\n"
                + "    SELECT order_id as country FROM `proj.ds.bronze_orders`\n"
                + ")\n"
                + "SELECT country FROM stats");
        ColumnWindowTarget target = targetOn(file, "country", "order_id as country");
        assertEquals("country", target.name());
    }

    /** The alias renames a column of another file, and that file is where it is declared. */
    public void testAnAliasIsDeclaredByTheColumnItRenames() throws Exception {
        open("bronze/bronze_orders.sqlx");
        PsiFile file = marts("WITH stats AS (\n"
                + "    SELECT order_id as country FROM `proj.ds.bronze_orders`\n"
                + ")\n"
                + "SELECT country FROM stats");
        ColumnWindowTarget target = targetOn(file, "country", "order_id as country");
        List<String> declared = new ArrayList<>();
        for (PsiElement element : target.declarations()) declared.add(locationOf(element));
        assertTrue("the renamed column is declared in the file it comes from, got " + declared,
                declared.contains("bronze_orders.sqlx:23"));
    }

    /** A column built from several columns names each of them. */
    public void testAnExpressionOverSeveralColumnsDeclaresThemAll() throws Exception {
        open("bronze/bronze_orders.sqlx");
        PsiFile file = marts("SELECT CONCAT(order_id, customer_id) AS combined\n"
                + "FROM `proj.ds.bronze_orders`");
        ColumnWindowTarget target = targetOn(file, "combined", "AS combined");
        assertEquals("both columns it is built from must be listed, got "
                        + target.declarations().size(), 2, target.declarations().size());
    }

    /** An alias is read by later queries of the same file, and those reads are its usages. */
    public void testAnAliasIsUsedByAnotherQueryOfTheFile() throws Exception {
        PsiFile file = marts("WITH stats AS (\n"
                + "    SELECT order_id as country FROM `proj.ds.bronze_orders`\n"
                + "),\n"
                + "counted AS (\n"
                + "    SELECT country, COUNT(*) AS n FROM stats GROUP BY country\n"
                + ")\n"
                + "SELECT country, n FROM counted");
        ColumnWindowTarget target = targetOn(file, "country", "order_id as country");
        List<String> rows = describe(ColumnUsageRows.of(getProject(), target, null));
        assertTrue("the read inside the next CTE must be listed, got " + rows,
                rows.stream().anyMatch(r -> r.startsWith("USAGE ")));
    }

    private String locationOf(PsiElement element) {
        InjectedLanguageManager manager = InjectedLanguageManager.getInstance(getProject());
        PsiFile host = manager.getTopLevelFile(element.getContainingFile());
        com.intellij.openapi.editor.Document document =
                com.intellij.psi.PsiDocumentManager.getInstance(getProject()).getDocument(host);
        int offset = manager.injectedToHost(element, element.getTextOffset());
        return host.getName() + ":" + (document.getLineNumber(offset) + 1);
    }

    /**
     * A declaration row is a column of another table, and carries the name that table gives it.
     * Showing the clicked column's name instead tells the reader that table has a column it does
     * not have.
     */
    public void testADeclarationRowCarriesTheDeclaringTablesName() throws Exception {
        PsiFile gold = openAll().get(2);
        int offset = gold.getText().indexOf("AS concatened_id") + "AS conc".length();
        ColumnWindowTarget target = ColumnWindowTarget.at(gold, offset);
        assertNotNull(target);
        assertEquals("concatened_id", target.name());

        List<ColumnUsageRow> rows = ColumnUsageRows.of(getProject(), target, null);
        for (ColumnUsageRow row : rows) {
            if (row.isHeading() || row.kind() != ColumnUsageRow.Kind.DECLARATION) continue;
            assertFalse("a declaration is named by its own file, not by the column clicked, got "
                    + row.name(), row.name().equals("concatened_id"));
        }
        assertTrue("the column it is built from must be listed",
                rows.stream().anyMatch(r -> !r.isHeading() && r.name().equals("customer_id")));
    }

    /** An alias is where the column is written, so it is never one of its own rows. */
    public void testAnAliasIsNotARowOfItself() throws Exception {
        PsiFile gold = openAll().get(2);
        int aliasLine = 38;
        int offset = gold.getText().indexOf("AS concatened_id") + "AS conc".length();
        ColumnWindowTarget target = ColumnWindowTarget.at(gold, offset);
        assertNotNull(target);
        for (ColumnUsageRow row : ColumnUsageRows.of(getProject(), target, null)) {
            if (row.isHeading()) continue;
            assertNotSame("the column must not depend on, or read, the line that names it",
                    "gold_customer_ltv.sqlx:" + aliasLine, row.location());
            assertFalse("the line naming the column is not one of its rows, got " + row.location(),
                    row.location().equals("gold_customer_ltv.sqlx:" + aliasLine));
        }
    }

    /**
     * A column the file names with {@code AS} is an output column of the table the file builds, and
     * what reads it lives in the files reading that table.
     */
    public void testAnAliasDeclaringAnOutputColumnIsReadAcrossFiles() throws Exception {
        open("gold/gold_customer_ltv.sqlx");
        PsiFile summary = open("gold/gold_customer_purchase_summary.sqlx");
        List<String> rows = describe(rowsAt(summary, 10, "order_amount"));
        assertTrue("gold_customer_ltv reads the column and must be listed, got " + rows,
                rows.contains("USAGE gold_customer_ltv.sqlx:18"));
        assertFalse("the alias is where the column is written, never a read of it, got " + rows,
                rows.contains("USAGE gold_customer_purchase_summary.sqlx:10"));
    }

    public void testReadsAreCapped() {
        assertTrue("an unbounded search would be paid for while the user waits",
                ColumnUsageRows.MAX_READS > 0 && ColumnUsageRows.MAX_READS <= 100);
    }

    /**
     * The window opens on the first reads and offers the rest on request, so the ceiling is the
     * caller's to set: a low one stops the search short and says so, no ceiling lists every read.
     */
    public void testTheCeilingStopsTheSearchAndLiftingItListsEveryRead() throws Exception {
        PsiFile silver = openAll().get(1);
        PsiElement injected = injectedAt(silver, 23, "order_id");
        int offset = InjectedLanguageManager.getInstance(getProject())
                .injectedToHost(injected, injected.getTextOffset());
        ColumnWindowTarget target = ColumnWindowTarget.at(silver, offset);
        assertNotNull(target);
        PsiElement caret = SqlxColumnAtCaret.referenceOf(injected);

        ColumnUsageRow capped = usagesHeading(ColumnUsageRows.of(getProject(), target, caret, 1));
        assertNotNull("the column has reads, so a Usages heading must lead them", capped);
        assertEquals("a ceiling of one keeps one read", 1, capped.count());
        assertTrue("a heading that stopped at the ceiling must say so", capped.isTruncated());

        ColumnUsageRow every = usagesHeading(
                ColumnUsageRows.of(getProject(), target, caret, ColumnUsageRows.UNBOUNDED));
        assertNotNull(every);
        assertTrue("without a ceiling every read is kept, got " + every.count(),
                every.count() > 1);
        assertFalse("a heading holding every read is not truncated", every.isTruncated());
    }

    private static ColumnUsageRow usagesHeading(List<ColumnUsageRow> rows) {
        for (ColumnUsageRow row : rows) {
            if (row.isHeading() && row.heading().equals(ColumnUsageRows.USAGES)) return row;
        }
        return null;
    }

    /**
     * The reads are searched for in parallel, so they are found in whatever order the threads
     * happen to finish. The order a reader sees has to come from the places themselves.
     */
    public void testReadsAreOrderedByFileThenLine() throws Exception {
        List<String> found = new ArrayList<>();
        for (ColumnUsageRow row : rowsAt(openAll().get(1), 23, "order_id")) {
            if (row.isHeading() || row.kind() != ColumnUsageRow.Kind.USAGE) continue;
            found.add(row.location());
        }
        List<String> ordered = new ArrayList<>(found);
        ordered.sort(java.util.Comparator
                .comparing((String at) -> at.substring(0, at.lastIndexOf(':')))
                .thenComparingInt(at -> Integer.parseInt(at.substring(at.lastIndexOf(':') + 1))));
        assertEquals("the window must impose an order a parallel search does not have, got " + found,
                ordered, found);
    }

    /** A window built on several threads must still show the same thing every time it is opened. */
    public void testTheSameColumnGivesTheSameWindowEveryTime() throws Exception {
        PsiFile silver = openAll().get(1);
        List<String> first = describe(rowsAt(silver, 23, "order_id"));
        assertFalse("the column must have rows for the comparison to mean anything", first.isEmpty());
        for (int run = 0; run < 3; run++) {
            assertEquals("a parallel search must not change what the window shows",
                    first, describe(rowsAt(silver, 23, "order_id")));
        }
    }

    /**
     * A count that stopped at the ceiling is not a total, and a reader takes a bare number for one.
     */
    public void testACappedHeadingSaysItStoppedAtTheCeiling() {
        assertTrue("a heading that hit the ceiling must say so",
                ColumnUsageRow.heading(ColumnUsageRows.USAGES, ColumnUsageRows.MAX_READS, true)
                        .isTruncated());
        assertFalse("a heading holding every read must not",
                ColumnUsageRow.heading(ColumnUsageRows.USAGES, 3, false).isTruncated());
    }

    /**
     * A column of a source declared with {@code declare()} has no query building it; its declaration
     * is the call naming the source, in the JavaScript file holding it.
     */
    public void testAColumnOfADeclaredSourceIsDeclaredByTheDeclareCall() throws Exception {
        open("sources.js");
        PsiFile bronze = open("bronze/bronze_events.sqlx");
        List<ColumnUsageRow> rows = rowsAt(bronze, 8, "event_id");
        List<String> described = describe(rows);
        assertTrue("the declare() call of the source is the declaration, got " + described,
                described.contains("DECLARATION sources.js:4"));
        ColumnUsageRow declaration = rows.stream()
                .filter(r -> !r.isHeading() && r.location().equals("sources.js:4"))
                .findFirst().orElseThrow();
        assertEquals("the row shows the line naming the source",
                "name: \"raw_events\"", declaration.before() + declaration.name() + declaration.after());
    }

    /**
     * The schema column of a source carries the file holding the {@code declare()} call, the very
     * file its declaration row points into. A schema column has no range of its own, and comparing
     * the two places must say they differ rather than fail.
     */
    public void testASourceColumnAndItsDeclareCallAreNotTheSamePlace() throws Exception {
        open("sources.js");
        ColumnOriginService origins = ColumnOriginService.getInstance(getProject());
        ColumnRef reference = new ColumnRef("proj.ds.raw_events", "event_id");
        DataformDasColumn column = origins.dasColumn(reference);
        PsiElement declaration = origins.sourceDeclaration(reference);
        assertNotNull(column);
        assertNotNull(declaration);
        assertEquals("the schema column reports the file of the declare() call",
                declaration.getContainingFile(), column.getContainingFile());

        ColumnWindowTarget target = new ColumnWindowTarget(List.of(column), "event_id",
                List.of(declaration), null);
        List<String> rows = describe(ColumnUsageRows.of(getProject(), target, null));
        assertTrue("the declare() call is listed as the declaration, got " + rows,
                rows.contains("DECLARATION sources.js:4"));
    }

    /**
     * An action may hand a column name to an include helper as a string, which the SQL the IDE
     * analyses never shows: the template becomes filler text. The name is nonetheless read there,
     * and the file reads the table declaring the column, so the window lists it.
     */
    public void testAColumnNamedAsAStringInATemplateOfAReaderIsAUsage() throws Exception {
        PsiFile silver = open("silver/silver_orders.sqlx");
        open("gold/gold_order_keys.sqlx");
        List<ColumnUsageRow> rows = rowsAt(silver, 23, "order_id");
        List<String> described = describe(rows);
        assertTrue("the string handed to the helper reads the column, got " + described,
                described.contains("USAGE gold_order_keys.sqlx:8"));
        ColumnUsageRow usage = rows.stream()
                .filter(r -> !r.isHeading() && r.location().equals("gold_order_keys.sqlx:8"))
                .findFirst().orElseThrow();
        assertEquals("the row shows the call as written",
                "${helpers.top_value(\"order_id\")} AS top_order",
                usage.before() + usage.name() + usage.after());
        Document document = FileDocumentManager.getInstance().getDocument(usage.target().getFile());
        assertNotNull(document);
        assertTrue("the row opens on the string itself", document.getText()
                .startsWith("\"order_id\"", usage.target().getOffset()));
    }
    /**
     * A column built by an include helper — {@code ${helpers.top_value("order_id")} AS top_order} —
     * is built from the columns the helper is handed as strings. The SQL the IDE analyses shows
     * the template as filler text, so no reference names those columns; the strings do, and each
     * is declared where its column is.
     */
    public void testAColumnBuiltByAHelperIsDeclaredByTheColumnsHandedToIt() throws Exception {
        open("silver/silver_orders.sqlx");
        PsiFile gold = open("gold/gold_order_keys.sqlx");
        int offset = gold.getText().indexOf("top_order") + 1;
        ColumnWindowTarget target = ColumnWindowTarget.at(gold, offset);
        assertNotNull("the alias of a helper-built column is still a column", target);
        List<String> declared = new ArrayList<>();
        for (PsiElement element : target.declarations()) declared.add(locationOf(element));
        assertTrue("the column handed to the helper is declared in silver, got " + declared,
                declared.contains("silver_orders.sqlx:23"));
        List<String> rows = describe(ColumnUsageRows.of(getProject(), target, null));
        assertTrue("the window has something to show, got " + rows,
                rows.contains("DECLARATION silver_orders.sqlx:23"));
    }
    /**
     * Once Node has evaluated the helper, its value is injected in place of the hole and the SQL
     * reads the column inside it. That text is not a place in the file — the reference search
     * walks the host's words and never reaches it — so the window keeps listing the string the
     * helper is handed, and only that.
     */
    public void testAReadInsideAnEvaluatedHoleIsListedAtTheStringAndNotAtTheHole() throws Exception {
        PsiFile silver = open("silver/silver_orders.sqlx");
        PsiFile gold = open("gold/gold_order_keys.sqlx");
        ((DataformExpressionEvaluationServiceImpl) DataformExpressionEvaluationService
                .getInstance(getProject())).putCachedValue(gold.getVirtualFile(),
                "helpers.top_value(\"order_id\")", "MAX(order_id)");
        SqlxInjectionRefresher.refresh(getProject(), gold.getVirtualFile());
        List<ColumnUsageRow> rows = rowsAt(silver, 23, "order_id");
        List<ColumnUsageRow> inGold = rows.stream()
                .filter(r -> !r.isHeading() && r.location().equals("gold_order_keys.sqlx:8"))
                .toList();
        assertEquals("one row for the helper call, got " + describe(rows), 1, inGold.size());
        Document document = FileDocumentManager.getInstance()
                .getDocument(inGold.getFirst().target().getFile());
        assertNotNull(document);
        assertTrue("the row opens on the string itself", document.getText()
                .startsWith("\"order_id\"", inGold.getFirst().target().getOffset()));
    }
    /**
     * With the helper evaluated, the alias is built from the column read in the value and from
     * the column named by the string, which are one and the same declaration.
     */
    public void testAnEvaluatedHelperDeclaresEachColumnOnce() throws Exception {
        open("silver/silver_orders.sqlx");
        PsiFile gold = open("gold/gold_order_keys.sqlx");
        ((DataformExpressionEvaluationServiceImpl) DataformExpressionEvaluationService
                .getInstance(getProject())).putCachedValue(gold.getVirtualFile(),
                "helpers.top_value(\"order_id\")", "MAX(order_id)");
        SqlxInjectionRefresher.refresh(getProject(), gold.getVirtualFile());
        ColumnWindowTarget target = ColumnWindowTarget.at(gold, gold.getText().indexOf("top_order") + 1);
        assertNotNull(target);
        List<String> declared = new ArrayList<>();
        for (PsiElement element : target.declarations()) declared.add(locationOf(element));
        assertEquals("got " + declared, List.of("silver_orders.sqlx:23"), declared);
    }
}
