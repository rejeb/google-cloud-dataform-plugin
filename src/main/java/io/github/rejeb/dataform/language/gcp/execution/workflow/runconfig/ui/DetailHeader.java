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
package io.github.rejeb.dataform.language.gcp.execution.workflow.runconfig.ui;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.BrowserLink;
import com.intellij.util.ui.JBUI;
import io.github.rejeb.dataform.language.ui.Toolbars;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * The header of a detail of the run console, as the prototype draws it: a status icon, a bold title,
 * a grayed detail or an id link, and icon buttons at the right end.
 */
final class DetailHeader extends JPanel {

    private static final int MIN_HEIGHT = 36;
    private static final int PADDING = 12;
    private static final int DETAIL_GAP = 8;

    private final SimpleColoredComponent text = new SimpleColoredComponent();
    private final JPanel lead = new JPanel(new BorderLayout(JBUI.scale(DETAIL_GAP), 0));
    private final JPanel linkHolder = new JPanel(new BorderLayout());
    private @Nullable BrowserLink detailLink;
    private final List<AnAction> actions;
    private String title = "";
    private String detail;

    /**
     * @param place      the action place of the buttons
     * @param bottomLine whether a line separates the header from what follows
     * @param actions    the icon buttons at the right end
     */
    DetailHeader(@NotNull String place, boolean bottomLine, @NotNull AnAction... actions) {
        super(new BorderLayout(JBUI.scale(PADDING), 0));
        this.actions = List.of(actions);
        setOpaque(false);
        setBorder(bottomLine
                ? JBUI.Borders.compound(JBUI.Borders.customLineBottom(JBUI.CurrentTheme.Separator.color()),
                JBUI.Borders.empty(0, PADDING))
                : JBUI.Borders.empty(0, PADDING));
        text.setOpaque(false);
        text.setIpad(JBUI.emptyInsets());
        lead.setOpaque(false);
        linkHolder.setOpaque(false);
        lead.add(text, BorderLayout.CENTER);
        add(lead, BorderLayout.CENTER);
        if (actions.length > 0) {
            ActionToolbar toolbar = Toolbars.horizontal(place, this, actions);
            add(toolbar.getComponent(), BorderLayout.EAST);
        }
    }

    /**
     * Shows a status icon, a bold title and a grayed detail. Must be called on the EDT.
     */
    void show(@Nullable Icon icon, @NotNull String title, @Nullable String detail) {
        show(icon, title, detail, null, "");
    }

    /**
     * Shows a status icon, a bold title and the detail as an id link opening the given URL, or grayed
     * when there is no URL. Must be called on the EDT.
     */
    void show(@Nullable Icon icon, @NotNull String title, @Nullable String detail, @Nullable String detailUrl,
              @NotNull String detailTooltip) {
        this.title = title;
        this.detail = detail;
        boolean hasDetail = detail != null && !detail.isBlank();
        text.clear();
        text.setIcon(icon);
        text.append(title, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
        if (hasDetail && detailUrl == null) {
            text.append("  " + detail, SimpleTextAttributes.GRAYED_ATTRIBUTES);
        }
        text.setToolTipText(title);
        lead.removeAll();
        linkHolder.removeAll();
        if (hasDetail && detailUrl != null) {
            detailLink = IdLinks.create(detail, detailUrl, detailTooltip);
            linkHolder.add(detailLink, BorderLayout.WEST);
            lead.add(text, BorderLayout.WEST);
            lead.add(linkHolder, BorderLayout.CENTER);
        } else {
            detailLink = null;
            lead.add(text, BorderLayout.CENTER);
        }
        revalidate();
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension size = super.getPreferredSize();
        return new Dimension(size.width, Math.max(size.height, JBUI.scale(MIN_HEIGHT)));
    }

    @Nullable
    Icon icon() {
        return text.getIcon();
    }

    @NotNull
    String title() {
        return title;
    }

    @Nullable
    String detail() {
        return detail;
    }

    @Nullable
    BrowserLink detailLink() {
        return detailLink;
    }

    @NotNull
    List<AnAction> actions() {
        return actions;
    }
}
