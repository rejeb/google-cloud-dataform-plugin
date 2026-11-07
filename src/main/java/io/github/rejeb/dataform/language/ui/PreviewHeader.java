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

import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.panels.HorizontalLayout;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * The row above the content of a preview tab: a grayed summary at the left, such as where its
 * content comes from and when, an optional trailing component at the right, over a separator line.
 */
public class PreviewHeader extends JPanel {

    private static final int MIN_HEIGHT = 36;
    private static final int PADDING = 12;
    private static final int GAP = 8;
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

    private final JBLabel summary = new JBLabel();
    private final JPanel leading = new JPanel(new HorizontalLayout(GAP, SwingConstants.CENTER));

    /**
     * Creates a header with an empty summary.
     */
    public PreviewHeader() {
        super(new BorderLayout(JBUI.scale(PADDING), 0));
        setOpaque(false);
        setBorder(JBUI.Borders.compound(
                JBUI.Borders.customLineBottom(JBUI.CurrentTheme.Separator.color()),
                JBUI.Borders.empty(0, PADDING)));

        summary.setFont(JBFont.medium());
        summary.setForeground(UIUtil.getContextHelpForeground());
        leading.setOpaque(false);
        leading.add(summary);
        add(leading, BorderLayout.WEST);
    }

    /**
     * The hour and minute of the given time, in the time zone of the system.
     */
    @NotNull
    public static String clock(long epochMillis) {
        return CLOCK.withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(epochMillis));
    }

    /**
     * Replaces the grayed summary text.
     */
    public void setSummary(@NotNull String text) {
        summary.setText(text);
        revalidate();
        repaint();
    }

    /**
     * Adds a component after the summary, such as a link.
     */
    public void addLeading(@NotNull JComponent component) {
        leading.add(component);
    }

    /**
     * Puts a component at the right end of the header, such as a toolbar.
     */
    public void setTrailing(@NotNull JComponent component) {
        add(component, BorderLayout.EAST);
        revalidate();
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension size = super.getPreferredSize();
        return new Dimension(size.width, Math.max(size.height, JBUI.scale(MIN_HEIGHT)));
    }

    /**
     * The summary text shown at the left.
     */
    @NotNull
    public String summaryText() {
        return summary.getText();
    }

    /**
     * The label holding the summary.
     */
    @NotNull
    public JBLabel summaryLabel() {
        return summary;
    }
}
