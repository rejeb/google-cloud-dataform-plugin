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
package io.github.rejeb.dataform.language.fileEditor;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class QueryDryRunErrorBannerTest {

    @Test
    public void onlyTheErrorsOfTheDisplayedActionsAreKept() {
        Map<String, String> all = Map.of(
                "p.ds.users", "boom",
                "p.ds.other_file_table", "other boom");

        Map<String, String> kept = QueryDryRunErrorBanner.errorsOf(List.of("p.ds.users"), all);

        assertEquals(Map.of("p.ds.users", "boom"), kept);
    }

    @Test
    public void displayedActionsWithoutErrorAreLeftOut() {
        Map<String, String> kept = QueryDryRunErrorBanner.errorsOf(
                List.of("p.ds.users"), Map.of());

        assertTrue(kept.isEmpty());
    }

    @Test
    public void keptErrorsFollowTheDisplayOrder() {
        Map<String, String> all = Map.of("p.ds.b", "second", "p.ds.a", "first");

        Map<String, String> kept = QueryDryRunErrorBanner.errorsOf(
                List.of("p.ds.b", "p.ds.a"), all);

        assertEquals(List.of("p.ds.b", "p.ds.a"), List.copyOf(kept.keySet()));
    }

    @Test
    public void aSingleErrorNamesItsActionAndMessage() {
        String text = QueryDryRunErrorBanner.bannerText(
                Map.of("p.ds.users", "Syntax error: Unexpected end of script at [3:5]"));

        assertEquals("BigQuery dry-run failed for p.ds.users: "
                + "Syntax error: Unexpected end of script at [3:5]", text);
    }

    @Test
    public void aLongMessageIsNotHardWrapped() {
        String message = "Access Denied: Table p.ds.users: User does not have permission to "
                + "query table p.ds.users, or perhaps it does not exist in location EU";
        String text = QueryDryRunErrorBanner.bannerText(Map.of("p.ds.users", message));

        assertFalse(text.contains("\n"), "wrapping must be left to the banner component");
        assertTrue(text.endsWith("location EU"), text);
    }

    @Test
    public void aMultiLineMessageIsCollapsedToOneLogicalLine() {
        String text = QueryDryRunErrorBanner.bannerText(
                Map.of("p.ds.users", "first line\n    second line"));

        assertEquals("BigQuery dry-run failed for p.ds.users: first line second line", text);
    }

    @Test
    public void severalErrorsAreBulletedOnSeparateLines() {
        Map<String, String> errors = new LinkedHashMap<>();
        errors.put("p.ds.users", "first problem");
        errors.put("p.ds.orders", "second problem");

        String text = QueryDryRunErrorBanner.bannerText(errors);

        assertEquals("BigQuery dry-run failed for 2 actions:\n"
                + "• p.ds.users: first problem\n"
                + "• p.ds.orders: second problem", text);
    }

    @Test
    public void anEmptyMessageIsReplacedByAPlaceholder() {
        String text = QueryDryRunErrorBanner.bannerText(Map.of("p.ds.users", "   "));

        assertEquals("BigQuery dry-run failed for p.ds.users: Unknown error", text);
    }
}
