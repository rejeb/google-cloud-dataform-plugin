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

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.sql.editor.SqlEditorOptions;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.testFramework.LoggedErrorProcessor;
import io.github.rejeb.dataform.language.schema.sql.DataformProjectFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

/**
 * A column picked from the completion popup is written under its bare name.
 *
 * <p>The SQL plugin qualifies what it inserts with the parent of the object whenever its
 * <em>Qualify object in</em> option lists basic completion, which it does by default. A column of a
 * Dataform action must be written as the query reads it, whatever that option says: the user should
 * not have to turn off a setting of another plugin to get a working query.</p>
 */
public class DataformColumnInsertContributorTest extends DataformProjectFixture {

    /**
     * Ignores the platform's complaint that the SQL plugin's own lookup element holds PSI. It is
     * raised by the SQL completion itself, has nothing to do with what is asserted here, and would
     * otherwise be the only thing this test could ever report.
     */
    private static final LoggedErrorProcessor IGNORING_SQL_LOOKUP_LEAK = new LoggedErrorProcessor() {
        @Override
        public @NotNull Set<Action> processError(@NotNull String category,
                                                 @NotNull String message,
                                                 String @NotNull [] details,
                                                 @Nullable Throwable throwable) {
            return message.contains("is retaining PSI")
                    ? EnumSet.noneOf(Action.class)
                    : EnumSet.allOf(Action.class);
        }
    };

    /**
     * Puts the SQL plugin in the state the user meets: <em>Qualify object in</em> listing basic
     * completion, which is what makes it write {@code table.column}.
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        SqlEditorOptions options = SqlEditorOptions.getInstance();
        SqlEditorOptions.QualificationType previous = options.getCompletionQualification();
        options.setCompletionQualification(SqlEditorOptions.QualificationType.ALWAYS);
        com.intellij.openapi.util.Disposer.register(getTestRootDisposable(),
                () -> options.setCompletionQualification(previous));
    }

    private String pick(String text, String name) {
        myFixture.configureByText("probe.sqlx", text);
        LookupElement[] elements = myFixture.completeBasic();
        assertNotNull("completing a select list must offer the table's columns", elements);
        LookupElement chosen = Arrays.stream(elements)
                .filter(element -> name.equals(element.getLookupString()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(name + " must be offered, got "
                        + Arrays.stream(elements).map(LookupElement::getLookupString).toList()));
        myFixture.getLookup().setCurrentItem(chosen);
        myFixture.type('\n');
        return InjectedLanguageManager.getInstance(getProject())
                .getTopLevelFile(myFixture.getFile()).getText();
    }

    public void testAColumnIsWrittenWithoutItsTable() throws Throwable {
        String[] result = new String[1];
        LoggedErrorProcessor.executeWith(IGNORING_SQL_LOOKUP_LEAK, () -> result[0] = pick(
                "config { type: \"table\" }\nSELECT <caret> FROM `proj.ds.bronze_orders`", "order_id"));

        assertEquals("config { type: \"table\" }\nSELECT order_id FROM `proj.ds.bronze_orders`",
                result[0]);
    }

    public void testAColumnAlreadyQualifiedByAnAliasKeepsThatAlias() throws Throwable {
        String[] result = new String[1];
        LoggedErrorProcessor.executeWith(IGNORING_SQL_LOOKUP_LEAK, () -> result[0] = pick(
                "config { type: \"table\" }\nSELECT bo.<caret> FROM ${ref(\"bronze_orders\")} bo",
                "order_id"));

        assertEquals("what the query already wrote is left alone; only the name is written here",
                "config { type: \"table\" }\nSELECT bo.order_id FROM ${ref(\"bronze_orders\")} bo",
                result[0]);
    }

    public void testAColumnOfATableReadThroughRefIsWrittenWithoutItsTable() throws Throwable {
        String[] result = new String[1];
        LoggedErrorProcessor.executeWith(IGNORING_SQL_LOOKUP_LEAK, () -> result[0] = pick(
                "config { type: \"table\" }\nSELECT <caret> FROM ${ref(\"bronze_orders\")}", "order_id"));

        assertEquals("config { type: \"table\" }\nSELECT order_id FROM ${ref(\"bronze_orders\")}",
                result[0]);
    }
}
