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
package io.github.rejeb.dataform.language.ui;

import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.StatusText;
import org.jetbrains.annotations.NotNull;

/**
 * Fills the text an empty tree, list or table paints in place of its rows.
 */
public final class EmptyStates {

    private EmptyStates() {
    }

    /**
     * Shows a single sentence.
     */
    public static void show(@NotNull StatusText text, @NotNull String primary) {
        text.clear();
        text.appendText(primary);
    }

    /**
     * Shows a sentence with a grayed explanation below it, such as "No rows".
     */
    public static void show(@NotNull StatusText text, @NotNull String primary, @NotNull String secondary) {
        show(text, primary);
        text.appendSecondaryText(secondary, SimpleTextAttributes.GRAYED_ATTRIBUTES, null);
    }

    /**
     * Shows a sentence with a link below it that runs an action, such as "Add repository…".
     */
    public static void show(@NotNull StatusText text, @NotNull String primary, @NotNull String link,
                            @NotNull Runnable onLink) {
        show(text, primary);
        text.appendSecondaryText(link, SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES, e -> onLink.run());
    }

    /**
     * Shows a sentence, a grayed explanation below it, and a link on its own line below that runs an
     * action, such as "No workspace selected", "Commits are made in a workspace.", "Select a
     * workspace…".
     */
    public static void showWithLinkBelow(@NotNull StatusText text, @NotNull String primary, @NotNull String secondary,
                                         @NotNull String link, @NotNull Runnable onLink) {
        show(text, primary);
        text.appendLine(secondary, SimpleTextAttributes.GRAYED_ATTRIBUTES, null);
        text.appendLine(link, SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES, e -> onLink.run());
    }

    /**
     * Shows a sentence with a grayed explanation below it, followed on the same line by a link that
     * runs an action, such as "The table does not exist in BigQuery yet. Refresh schema".
     */
    public static void show(@NotNull StatusText text, @NotNull String primary, @NotNull String secondary,
                            @NotNull String link, @NotNull Runnable onLink) {
        show(text, primary);
        text.appendSecondaryText(secondary + " ", SimpleTextAttributes.GRAYED_ATTRIBUTES, null);
        text.appendSecondaryText(link, SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES, e -> onLink.run());
    }
}
