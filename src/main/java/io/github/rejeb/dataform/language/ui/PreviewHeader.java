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

import com.intellij.ui.ClientProperty;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.panels.HorizontalLayout;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.SwingTextTrimmer;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.ToolTipManager;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.MouseEvent;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * The row above the content of a preview tab: a grayed summary at the left, such as where its
 * content comes from and when, an optional trailing component at the right, over a separator line.
 * A header narrower than its content keeps the whole trailing component and shortens the summary
 * at its end, the whole summary then showing in its tooltip, then the components added after it,
 * so nothing overlaps.
 */
public class PreviewHeader extends JPanel {

    private static final int MIN_HEIGHT = 36;
    private static final int PADDING = 12;
    private static final int GAP = 8;
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

    private final JBLabel summary = new SummaryLabel();
    private final JPanel extras = new ExtrasPanel();
    private final HeaderRowLayout layout;
    private @Nullable JComponent trailing;

    /**
     * Creates a header with an empty summary.
     */
    public PreviewHeader() {
        setOpaque(false);
        setBorder(JBUI.Borders.compound(
                JBUI.Borders.customLineBottom(JBUI.CurrentTheme.Separator.color()),
                JBUI.Borders.empty(0, PADDING)));

        summary.setFont(JBFont.medium());
        summary.setForeground(UIUtil.getContextHelpForeground());
        ClientProperty.put(summary, SwingTextTrimmer.KEY, SwingTextTrimmer.ELLIPSIS_AT_RIGHT);
        JPanel filler = new JPanel();
        filler.setOpaque(false);

        layout = new HeaderRowLayout(List.of(), summary, extras, filler, GAP, PADDING);
        setLayout(layout);
        add(summary);
        add(extras);
        add(filler);
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
        extras.add(component);
        revalidate();
        repaint();
    }

    /**
     * Puts a component at the right end of the header, such as a toolbar, in place of the previous
     * one.
     */
    public void setTrailing(@NotNull JComponent component) {
        if (trailing != null) {
            remove(trailing);
        }
        trailing = component;
        add(component);
        layout.setTrailing(component);
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

    private static final class ExtrasPanel extends JPanel {

        private ExtrasPanel() {
            super(new HorizontalLayout(GAP, SwingConstants.CENTER));
            setOpaque(false);
        }

        @Override
        public void doLayout() {
            super.doLayout();
            for (Component child : getComponents()) {
                child.setSize(Math.max(0, Math.min(child.getWidth(), getWidth() - child.getX())), child.getHeight());
            }
        }
    }

    private static final class SummaryLabel extends JBLabel {

        private SummaryLabel() {
            ToolTipManager.sharedInstance().registerComponent(this);
        }

        @Override
        public @Nullable String getToolTipText(@NotNull MouseEvent event) {
            return getWidth() < getPreferredSize().width ? getText() : null;
        }
    }
}
