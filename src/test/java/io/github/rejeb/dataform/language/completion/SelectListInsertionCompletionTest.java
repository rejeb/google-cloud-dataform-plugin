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

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.testFramework.LoggedErrorProcessor;
import io.github.rejeb.dataform.language.schema.sql.DataformProjectFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Columns are offered when a new item is typed into a select list ahead of an existing one,
 * before the comma that will separate them has been written.
 */
public class SelectListInsertionCompletionTest extends DataformProjectFixture {

    /**
     * Ignores the platform's complaint that the SQL plugin's own lookup element holds PSI. It is
     * raised by the SQL completion itself and has nothing to do with what is asserted here.
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

    private List<String> offered(String text) {
        List<List<String>> result = new ArrayList<>();
        try {
            LoggedErrorProcessor.executeWith(IGNORING_SQL_LOOKUP_LEAK, () -> {
                myFixture.configureByText("probe.sqlx", text);
                LookupElement[] elements = myFixture.completeBasic();
                result.add(elements == null ? List.of()
                        : Arrays.stream(elements).map(LookupElement::getLookupString).toList());
            });
        } catch (Throwable e) {
            throw new AssertionError(e);
        }
        return result.get(0);
    }

    public void testBaselineAfterACommaOffersColumns() {
        assertContainsElements(offered(
                "config { type: \"table\" }\nSELECT cus<caret>, order_id FROM `proj.ds.bronze_orders`"),
                "customer_id");
    }

    public void testAMultiMatchPrefixBeforeAnExistingItemWithoutACommaOffersColumns() {
        assertContainsElements(offered(
                "config { type: \"table\" }\nSELECT order<caret> customer_id FROM `proj.ds.bronze_orders`"),
                "order_id", "order_ts", "order_status");
    }

    public void testAMultiMatchPrefixBeforeAnExistingItemOfARefTableOffersColumns() {
        assertContainsElements(offered(
                "config { type: \"table\" }\nSELECT order<caret> customer_id FROM ${ref(\"bronze_orders\")}"),
                "order_id", "order_ts", "order_status");
    }

    public void testAPrefixBeforeAQualifiedItemWithoutACommaOffersColumns() {
        assertContainsElements(offered(
                "config { type: \"table\" }\nSELECT order<caret> bo.customer_id FROM `proj.ds.bronze_orders` bo"),
                "order_id", "order_ts", "order_status");
    }

    public void testAnEmptyPositionBeforeAQualifiedItemWithoutACommaOffersColumns() {
        assertContainsElements(offered(
                "config { type: \"table\" }\nSELECT <caret> bo.customer_id FROM `proj.ds.bronze_orders` bo"),
                "order_id");
    }

    public void testAPrefixBeforeAnAliasedItemWithoutACommaOffersColumns() {
        assertContainsElements(offered(
                "config { type: \"table\" }\nSELECT order<caret> customer_id AS cid FROM `proj.ds.bronze_orders`"),
                "order_id", "order_ts", "order_status");
    }

    public void testAPrefixBeforeAFunctionCallWithoutACommaOffersColumns() {
        assertContainsElements(offered(
                "config { type: \"table\" }\nSELECT order<caret> COUNT(*) FROM `proj.ds.bronze_orders`"),
                "order_id", "order_ts", "order_status");
    }

    public void testAPrefixBeforeAQualifiedItemOfACteOffersItsColumns() {
        assertContainsElements(offered(
                "config { type: \"table\" }\nWITH t AS (SELECT 1 AS order_id, 2 AS order_ts, 3 AS customer_id)\n"
                + "SELECT order<caret> t.customer_id FROM t"),
                "order_id", "order_ts");
    }

    public void testAQualifiedPrefixBeforeAQualifiedItemWithoutACommaOffersColumns() {
        assertContainsElements(offered(
                "config { type: \"table\" }\nSELECT bo.order<caret> bo.customer_id FROM `proj.ds.bronze_orders` bo"),
                "order_id", "order_ts", "order_status");
    }

    public void testAQualifierAloneBeforeAQualifiedItemWithoutACommaOffersColumns() {
        assertContainsElements(offered(
                "config { type: \"table\" }\nSELECT bo.<caret> bo.customer_id FROM `proj.ds.bronze_orders` bo"),
                "order_id", "customer_id");
    }

    public void testAQualifiedPrefixAfterACommaBeforeAQualifiedItemOffersColumns() {
        assertContainsElements(offered(
                "config { type: \"table\" }\nSELECT bo.order_ts, bo.order<caret> bo.customer_id FROM `proj.ds.bronze_orders` bo"),
                "order_id", "order_status");
    }

    public void testAPickedColumnReplacesOnlyThePrefixAndLeavesTheCommaToTheUser() {
        String[] result = new String[1];
        try {
            LoggedErrorProcessor.executeWith(IGNORING_SQL_LOOKUP_LEAK, () -> {
                myFixture.configureByText("probe.sqlx",
                        "config { type: \"table\" }\nSELECT bo.order_t<caret> bo.customer_id FROM `proj.ds.bronze_orders` bo");
                LookupElement[] elements = myFixture.completeBasic();
                if (elements != null) {
                    LookupElement chosen = Arrays.stream(elements)
                            .filter(element -> "order_ts".equals(element.getLookupString()))
                            .findFirst().orElseThrow();
                    myFixture.getLookup().setCurrentItem(chosen);
                    myFixture.type('\n');
                }
                result[0] = com.intellij.lang.injection.InjectedLanguageManager.getInstance(getProject())
                        .getTopLevelFile(myFixture.getFile()).getText();
            });
        } catch (Throwable e) {
            throw new AssertionError(e);
        }
        assertEquals("config { type: \"table\" }\nSELECT bo.order_ts bo.customer_id FROM `proj.ds.bronze_orders` bo",
                result[0]);
    }

    public void testAnEmptyPositionBeforeAnExistingItemWithoutACommaOffersColumns() {
        assertContainsElements(offered(
                "config { type: \"table\" }\nSELECT <caret> order_id FROM `proj.ds.bronze_orders`"),
                "customer_id");
    }
}
