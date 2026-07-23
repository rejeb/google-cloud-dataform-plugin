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
package io.github.rejeb.dataform.language.injection;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.lang.javascript.psi.ecma6.JSStringTemplateExpression;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.compilation.DataformCompilationService;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.compilation.model.CompiledTable;
import io.github.rejeb.dataform.language.compilation.model.Target;

import java.lang.reflect.Field;
import java.util.List;

public class SqlxJsQueryInjectorRefCompletionTest extends BasePlatformTestCase {

    private static void set(Object target, String field, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new IllegalStateException("set " + field, e);
        }
    }

    private void installGraphWithTable(String tableName) {
        Target t = new Target();
        set(t, "database", "proj");
        set(t, "schema", "ds");
        set(t, "name", tableName);
        CompiledTable table = new CompiledTable();
        set(table, "type", "table");
        set(table, "target", t);
        set(table, "fileName", "definitions/" + tableName + ".sqlx");
        set(table, "tags", List.of());
        set(table, "dependencyTargets", List.of());
        CompiledGraph graph = new CompiledGraph();
        set(graph, "tables", List.of(table));
        set(graph, "declarations", List.of());
        set(graph, "operations", List.of());
        set(graph, "assertions", List.of());
        set(getProject().getService(DataformCompilationService.class), "compiledGraph", graph);
    }

    private PsiFile configureDefinition(String name, String text) {
        PsiFile f = myFixture.addFileToProject("definitions/" + name, text);
        myFixture.configureFromExistingVirtualFile(f.getVirtualFile());
        return myFixture.getFile();
    }

    public void testRefCompletionWorksInJsQuery() {
        installGraphWithTable("team_players_stat");
        configureDefinition("mart.js",
                "publish(\"x\").query(ctx => `SELECT * FROM ${ctx.ref(\"<caret>\")}`);");

        myFixture.completeBasic();
        List<String> lookups = myFixture.getLookupElementStrings();

        assertNotNull("expected a completion popup for ref()", lookups);
        assertTrue("ref() autocomplete must offer known tables, got " + lookups,
                lookups.contains("team_players_stat"));
    }

    public void testRefCompletionWorksAlongsideNonRefHole() {
        installGraphWithTable("team_players_stat");
        configureDefinition("mart2.js",
                "const view = { columnToSelect: \"c\" };\n"
                        + "publish(\"x\").query(ctx => "
                        + "`SELECT ${view.columnToSelect} FROM ${ctx.ref(\"<caret>\")}`);");

        myFixture.completeBasic();
        List<String> lookups = myFixture.getLookupElementStrings();

        assertNotNull(lookups);
        assertTrue("ref() autocomplete must work even with a non-ref hole present, got " + lookups,
                lookups.contains("team_players_stat"));
    }

    public void testRefResolveStillWorksInJsQuery() {
        installGraphWithTable("team_players_stat");
        myFixture.addFileToProject("definitions/team_players_stat.sqlx",
                "config { type: \"table\" }\nSELECT 1");
        PsiFile file = configureDefinition("mart3.js",
                "publish(\"x\").query(ctx => `SELECT * FROM ${ctx.ref(\"team_play<caret>ers_stat\")}`);");

        PsiReference ref = file.findReferenceAt(myFixture.getCaretOffset());
        assertNotNull("ref() must still resolve in .js definition files", ref);
        assertNotNull("ref() must resolve to a target", ref.resolve());
    }

    public void testTemplateWithoutRefHoleStillGetsSqlInjection() {
        installGraphWithTable("team_players_stat");
        PsiFile file = configureDefinition("mart4.js",
                "const view = { col: \"c\" };\n"
                        + "publish(\"x\").query(ctx => `SELECT ${view.col} FROM some_table`);");

        JSStringTemplateExpression tpl =
                PsiTreeUtil.findChildOfType(file, JSStringTemplateExpression.class);
        assertNotNull(tpl);
        int sqlOffset = file.getText().indexOf("some_table") + 2;
        PsiElement injected =
                InjectedLanguageManager.getInstance(getProject()).findInjectedElementAt(file, sqlOffset);
        assertNotNull("templates without ref() holes must still receive SQL injection", injected);
        assertEquals("BigQuery", injected.getContainingFile().getLanguage().getID());
    }

    public void testNoDuplicatesInPlainJs() {
        installGraphWithTable("team_players_stat");
        PsiFile f = myFixture.addFileToProject("scripts/p.js", "const a = ref(\"<caret>\");");
        myFixture.configureFromExistingVirtualFile(f.getVirtualFile());
        myFixture.completeBasic();
        List<String> lk = myFixture.getLookupElementStrings();
        assertNotNull(lk);
        long n = lk.stream().filter("team_players_stat"::equals).count();
        assertEquals("no duplicate ref completions in plain .js, got " + lk, 1, n);
    }

    public void testNoDuplicatesInSqlx() {
        installGraphWithTable("team_players_stat");
        myFixture.configureByText("d.sqlx",
                "config { type: \"table\" }\nSELECT * FROM ${ctx.ref(\"<caret>\")}");
        myFixture.completeBasic();
        List<String> lk = myFixture.getLookupElementStrings();
        assertNotNull(lk);
        long n = lk.stream().filter("team_players_stat"::equals).count();
        assertEquals("no duplicate ref completions in .sqlx, got " + lk, 1, n);
    }
}
