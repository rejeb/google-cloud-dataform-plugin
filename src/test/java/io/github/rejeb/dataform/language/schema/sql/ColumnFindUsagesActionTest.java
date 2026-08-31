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

import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.find.findUsages.FindUsagesHandlerFactory;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.psi.PsiElement;
import com.intellij.usageView.UsageInfo;
import io.github.rejeb.dataform.language.lineage.column.ColumnRef;
import io.github.rejeb.dataform.language.schema.sql.model.DataformDasColumn;
import io.github.rejeb.dataform.language.schema.sql.model.DataformDasTable;

import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

/**
 * Find Usages is an action, and an action needs a handler. Reference search alone finding the
 * usages is not enough: without a {@link FindUsagesHandler} accepting the element, the action
 * cannot run at all and the user is told nothing can be searched.
 */
public class ColumnFindUsagesActionTest extends DataformProjectFixture {

    private FindUsagesHandler handlerFor(PsiElement element) {
        for (FindUsagesHandlerFactory factory
                : FindUsagesHandlerFactory.EP_NAME.getExtensions(getProject())) {
            if (factory.canFindUsages(element)) {
                return factory.createFindUsagesHandler(element, false);
            }
        }
        return null;
    }

    private Set<String> usageFilesOf(PsiElement element) {
        FindUsagesHandler handler = handlerFor(element);
        assertNotNull("Find Usages must have a handler for " + element, handler);
        Collection<UsageInfo> usages = handler.findReferencesToHighlight(element,
                com.intellij.psi.search.GlobalSearchScope.projectScope(getProject()))
                .stream()
                .map(UsageInfo::new)
                .toList();
        InjectedLanguageManager manager = InjectedLanguageManager.getInstance(getProject());
        Set<String> files = new TreeSet<>();
        for (UsageInfo usage : usages) {
            PsiElement found = usage.getElement();
            if (found == null) continue;
            files.add(manager.getTopLevelFile(found.getContainingFile()).getName());
        }
        return files;
    }

    private void openAll() throws Exception {
        open("bronze/bronze_orders.sqlx");
        open("silver/silver_orders.sqlx");
        open("gold/gold_customer_ltv.sqlx");
    }

    public void testFindUsagesHasAHandlerForAColumn() throws Exception {
        openAll();
        DataformDasColumn column = ColumnOriginService.getInstance(getProject())
                .dasColumn(new ColumnRef("proj.ds.bronze_orders", "order_id"));
        assertNotNull(column);
        assertNotNull("without a handler the Find Usages action cannot run on a column",
                handlerFor(column));
    }

    public void testColumnUsagesSpanEveryFileThatReadsIt() throws Exception {
        openAll();
        DataformDasColumn column = ColumnOriginService.getInstance(getProject())
                .dasColumn(new ColumnRef("proj.ds.bronze_orders", "order_id"));
        assertNotNull(column);
        Set<String> files = usageFilesOf(column);
        assertTrue("the file declaring the column must be listed, got " + files,
                files.contains("bronze_orders.sqlx"));
        assertTrue("the file reading the column must be listed, got " + files,
                files.contains("silver_orders.sqlx"));
    }

    /**
     * The path the Find Usages action itself takes. Before a handler existed this failed with
     * "Cannot find handler for: order_id" rather than returning no usages.
     */
    public void testFindUsagesActionListsCrossFileUsages() throws Exception {
        openAll();
        DataformDasColumn column = ColumnOriginService.getInstance(getProject())
                .dasColumn(new ColumnRef("proj.ds.bronze_orders", "order_id"));
        assertNotNull(column);
        InjectedLanguageManager manager = InjectedLanguageManager.getInstance(getProject());
        Set<String> files = new TreeSet<>();
        for (UsageInfo usage : myFixture.findUsages(column)) {
            PsiElement found = usage.getElement();
            if (found == null) continue;
            files.add(manager.getTopLevelFile(found.getContainingFile()).getName());
        }
        assertTrue("the file reading the column must be listed, got " + files,
                files.contains("silver_orders.sqlx"));
    }

    /** The {@code AS} expression Find Usages targets when the caret sits on an alias. */
    private PsiElement aliasAt(com.intellij.psi.PsiFile file, String alias) {
        int offset = file.getText().indexOf(alias) + alias.length() - 1;
        PsiElement token = InjectedLanguageManager.getInstance(getProject())
                .findInjectedElementAt(file, offset);
        assertNotNull("the alias must sit inside the injected SQL", token);
        return token.getParent().getParent();
    }

    private Set<String> actionUsageFilesOf(PsiElement element) {
        InjectedLanguageManager manager = InjectedLanguageManager.getInstance(getProject());
        Set<String> files = new TreeSet<>();
        for (UsageInfo usage : myFixture.findUsages(element)) {
            PsiElement found = usage.getElement();
            if (found == null) continue;
            files.add(manager.getTopLevelFile(found.getContainingFile()).getName());
        }
        return files;
    }

    /**
     * An alias of the main select list names an output column of the table the file builds, and
     * Find Usages on it has to reach the files reading that table.
     */
    public void testFindUsagesOnAnAliasListsTheFilesReadingTheColumn() throws Exception {
        open("gold/gold_customer_ltv.sqlx");
        com.intellij.psi.PsiFile summary = open("gold/gold_customer_purchase_summary.sqlx");
        PsiElement alias = aliasAt(summary, "AS order_amount");
        assertNotNull("Find Usages must have a handler for an alias declaring a column",
                handlerFor(alias));
        Set<String> files = actionUsageFilesOf(alias);
        assertTrue("the file reading the column must be listed, got " + files,
                files.contains("gold_customer_ltv.sqlx"));
        assertTrue("what the SQL plugin found on the alias alone must be kept, got " + files,
                files.contains("gold_customer_purchase_summary.sqlx"));
    }

    /**
     * A name a query gives itself declares no output column, so the SQL plugin keeps it: claiming
     * every alias of every SQL file would answer for names this plugin knows nothing about.
     */
    public void testAnAliasOfACommonTableExpressionIsLeftToTheSqlPlugin() throws Exception {
        com.intellij.psi.PsiFile file = myFixture.addFileToProject("definitions/marts.sqlx",
                "config { type: \"table\" }\n"
                        + "WITH stats AS (\n"
                        + "    SELECT order_id AS country FROM `proj.ds.bronze_orders`\n"
                        + ")\n"
                        + "SELECT country FROM stats");
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        PsiElement alias = aliasAt(myFixture.getFile(), "AS country");
        assertNull("a common table expression names no column of the table the file builds",
                SqlxColumnAtCaret.declaredColumnOf(alias));
        assertFalse("this plugin has nothing to add to a name local to the query",
                new DataformSchemaFindUsagesHandlerFactory().canFindUsages(alias));
    }

    public void testFindUsagesHasAHandlerForATable() throws Exception {
        openAll();
        DataformDasTable table = DataformTableSchemaService.getInstance(getProject())
                .getAllTables().get("proj.ds.bronze_orders");
        assertNotNull(table);
        assertNotNull("without a handler the Find Usages action cannot run on a table",
                handlerFor(table));
    }
}
