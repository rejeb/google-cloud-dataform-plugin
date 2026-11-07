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

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.InlineBanner;
import io.github.rejeb.dataform.language.diagnostics.BannerText;
import org.jetbrains.annotations.NotNull;

/**
 * Factory of the inline banners that report a problem inside a view, next to what it is about.
 * The banner shows the message on one logical line, shortened when long, and keeps the whole
 * message in its tooltip.
 */
public final class Banners {

    private static final int MAX_VISIBLE_LENGTH = 400;
    private static final String ELLIPSIS = "…";
    private static final String NO_MESSAGE = "No message";

    private Banners() {
    }

    /**
     * Creates an error banner.
     */
    public static @NotNull InlineBanner error(@NotNull String message) {
        return create(message, EditorNotificationPanel.Status.Error);
    }

    /**
     * Creates an error banner whose bold title stands above the message.
     */
    public static @NotNull InlineBanner error(@NotNull String title, @NotNull String message) {
        InlineBanner banner = create(message, EditorNotificationPanel.Status.Error);
        setMessage(banner, title, message);
        return banner;
    }

    /**
     * Creates a warning banner.
     */
    public static @NotNull InlineBanner warning(@NotNull String message) {
        return create(message, EditorNotificationPanel.Status.Warning);
    }

    /**
     * Creates an information banner.
     */
    public static @NotNull InlineBanner info(@NotNull String message) {
        return create(message, EditorNotificationPanel.Status.Info);
    }

    /**
     * Replaces the message of a banner created here.
     */
    public static void setMessage(@NotNull InlineBanner banner, @NotNull String message) {
        banner.setMessage(visibleText(message));
        banner.setToolTipText(tooltip(message));
    }

    /**
     * Replaces the message of a banner created here, under a bold title.
     */
    public static void setMessage(@NotNull InlineBanner banner, @NotNull String title, @NotNull String message) {
        banner.setMessage("<b>" + StringUtil.escapeXmlEntities(title) + "</b><br>" + visibleText(message));
        banner.setToolTipText(tooltip(message));
    }

    private static @NotNull InlineBanner create(@NotNull String message, @NotNull EditorNotificationPanel.Status status) {
        InlineBanner banner = new InlineBanner(visibleText(message), status);
        banner.showCloseButton(false);
        banner.setToolTipText(tooltip(message));
        return banner;
    }

    private static @NotNull String visibleText(@NotNull String message) {
        String collapsed = BannerText.collapse(message, NO_MESSAGE);
        String shortened = collapsed.length() <= MAX_VISIBLE_LENGTH
                ? collapsed
                : collapsed.substring(0, MAX_VISIBLE_LENGTH - ELLIPSIS.length()) + ELLIPSIS;
        return StringUtil.escapeXmlEntities(shortened);
    }

    private static @NotNull String tooltip(@NotNull String message) {
        return UiText.htmlTooltip(message.isBlank() ? NO_MESSAGE : message);
    }
}
