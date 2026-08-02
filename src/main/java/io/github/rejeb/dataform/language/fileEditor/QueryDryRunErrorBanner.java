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

import io.github.rejeb.dataform.language.diagnostics.BannerText;
import io.github.rejeb.dataform.language.diagnostics.WrappingEditorNotificationPanel;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Banner shown at the top of the query view reporting the errors BigQuery answered the dry-run
 * of the displayed actions with. It hides itself as soon as every dry-run runs clean.
 */
public final class QueryDryRunErrorBanner extends WrappingEditorNotificationPanel {

    private static final String PREFIX = "BigQuery dry-run failed";
    private static final String UNKNOWN_ERROR = "Unknown error";

    public QueryDryRunErrorBanner() {
        super(Status.Error, "");
        setVisible(false);
    }

    /**
     * Shows the dry-run errors of the given actions, in the order they are displayed, and hides
     * the banner when none of them failed.
     */
    public void update(@NotNull Map<String, String> errorsByAction) {
        if (errorsByAction.isEmpty()) {
            setVisible(false);
            return;
        }
        setWrappingText(bannerText(errorsByAction));
        setToolTipText(tooltip(errorsByAction));
        setVisible(true);
        revalidate();
        repaint();
    }

    /**
     * Keeps the errors of the given actions, preserving their display order.
     */
    @NotNull
    static Map<String, String> errorsOf(@NotNull List<String> actionFullNames,
                                        @NotNull Map<String, String> allErrors) {
        Map<String, String> errors = new LinkedHashMap<>();
        actionFullNames.stream()
                .filter(allErrors::containsKey)
                .forEach(name -> errors.put(name, allErrors.get(name)));
        return errors;
    }

    /**
     * Renders the dry-run errors as plain text, each prefixed by the action it belongs to.
     */
    @NotNull
    static String bannerText(@NotNull Map<String, String> errorsByAction) {
        String header = errorsByAction.size() == 1
                ? PREFIX + " for " + errorsByAction.keySet().iterator().next() + ":"
                : PREFIX + " for " + errorsByAction.size() + " actions:";
        List<String> messages = errorsByAction.size() == 1
                ? List.of(errorsByAction.values().iterator().next())
                : describeEach(errorsByAction);
        return BannerText.of(header, messages, UNKNOWN_ERROR);
    }

    @NotNull
    private static String tooltip(@NotNull Map<String, String> errorsByAction) {
        return String.join("\n", describeEach(errorsByAction));
    }

    @NotNull
    private static List<String> describeEach(@NotNull Map<String, String> errorsByAction) {
        return errorsByAction.entrySet().stream()
                .map(e -> e.getKey() + ": " + BannerText.collapse(e.getValue(), UNKNOWN_ERROR))
                .toList();
    }
}
