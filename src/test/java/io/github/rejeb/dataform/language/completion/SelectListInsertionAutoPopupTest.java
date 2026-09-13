/*
 * Copyright 2025 Rejeb Ben Rejeb
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.rejeb.dataform.language.completion;

import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.LoggedErrorProcessor;
import com.intellij.testFramework.fixtures.CompletionAutoPopupTester;
import io.github.rejeb.dataform.language.schema.sql.DataformProjectFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The completion popup opens on its own while a new item is typed into a select list ahead of
 * an existing one, before the comma that will separate them has been written.
 */
public class SelectListInsertionAutoPopupTest extends DataformProjectFixture {

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

    @Override
    protected boolean runInDispatchThread() {
        return false;
    }

    private String popupAfterTyping(String text, String typed) {
        List<String> shown = new java.util.ArrayList<>();
        try {
            LoggedErrorProcessor.executeWith(IGNORING_SQL_LOOKUP_LEAK, () -> {
                CompletionAutoPopupTester tester = new CompletionAutoPopupTester(myFixture);
                tester.runWithAutoPopupEnabled(() -> {
                    EdtTestUtil.runInEdtAndWait(() -> myFixture.configureByText("probe.sqlx", text));
                    tester.typeWithPauses(typed);
                    Lookup lookup = tester.getLookup();
                    shown.add(lookup == null ? "<no popup>"
                            : String.join(",", myFixture.getLookupElementStrings()));
                });
            });
        } catch (Throwable e) {
            throw new AssertionError(e);
        }
        return shown.get(0);
    }

    public void testBaselineTypingAfterACommaPopsUpColumns() {
        String shown = popupAfterTyping(
                "config { type: \"table\" }\nSELECT <caret>, customer_id FROM `proj.ds.bronze_orders`", "or");
        assertTrue(shown, shown.contains("order_id"));
    }

    public void testTypingBeforeAnExistingItemWithoutACommaPopsUpColumns() {
        String shown = popupAfterTyping(
                "config { type: \"table\" }\nSELECT <caret> customer_id FROM `proj.ds.bronze_orders`", "or");
        assertTrue(shown, shown.contains("order_id"));
    }
}
