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

import com.intellij.ide.ui.UISettings;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.render.RenderingUtil;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.JBUI;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.BigQueryJobDetails.BigQueryChildJob;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;

/**
 * Paints the Timeline column of the child jobs table: the grid lines at the ticks, the bar of the
 * statement and its label; and the column header, with the tick labels.
 */
final class TimelineCellRenderer extends JComponent implements TableCellRenderer {

    private static final int BAR_HEIGHT = 16;
    private static final int MIN_BAR_WIDTH = 4;
    private static final int BAR_ARC = 6;
    private static final int LABEL_GAP = 6;
    private static final int HEADER_HEIGHT = 20;
    private static final JBColor BAR = new JBColor(0xA8C3F5, 0x35538F);
    private static final JBColor BAR_SELECTED = new JBColor(0x3574F0, 0x548AF7);
    private static final JBColor FAILED_BAR = new JBColor(0xF0A3A8, 0x9E4448);
    private static final JBColor FAILED_BAR_SELECTED = new JBColor(0xE55765, 0xE06C70);
    private static final JBColor LABEL_INSIDE_SELECTED = new JBColor(0xFFFFFF, 0x141A26);
    private static final JBColor LABEL_INSIDE = new JBColor(0x1E1F22, 0xDFE1E5);
    private static final String FAILED = "FAILED";

    /**
     * Where the label of a bar is drawn.
     */
    enum LabelPlacement {AFTER, INSIDE, NONE}

    private final ChildJobsTimeline table;
    private @Nullable BigQueryChildJob job;
    private boolean selected;

    /**
     * Creates the renderer of the Timeline cells of the given table.
     */
    TimelineCellRenderer(@NotNull ChildJobsTimeline table) {
        this.table = table;
        setOpaque(true);
    }

    @Override
    public Component getTableCellRendererComponent(JTable jTable, Object value, boolean isSelected, boolean hasFocus,
                                                   int row, int column) {
        job = value instanceof BigQueryChildJob child ? child : null;
        selected = isSelected;
        setBackground(RenderingUtil.getBackground(jTable, isSelected));
        return this;
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(0, table.getRowHeight());
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setColor(getBackground());
            g2.fillRect(0, 0, getWidth(), getHeight());
            paintGrid(g2, table.statementTimeline(), getWidth(), getHeight());
            if (job != null) paintBar(g2, job);
        } finally {
            g2.dispose();
        }
    }

    /**
     * The bounds of a bar in a track: at least {@code 4 px} wide (scaled), never past the track end,
     * centred in the row.
     */
    @NotNull
    static Rectangle barBounds(@NotNull StatementTimeline.Bar bar, int trackWidth, int rowHeight) {
        int height = JBUI.scale(BAR_HEIGHT);
        int width = Math.min(trackWidth, Math.max(JBUI.scale(MIN_BAR_WIDTH),
                (int) Math.round((bar.to() - bar.from()) * trackWidth)));
        int x = (int) Math.round(bar.from() * trackWidth);
        if (x + width > trackWidth) x = Math.max(0, trackWidth - width);
        return new Rectangle(x, (rowHeight - height) / 2, width, height);
    }

    /**
     * Where a label of the given width goes: after the bar when it fits in the track, else inside the
     * bar at its right end when it fits there, else nowhere.
     */
    @NotNull
    static LabelPlacement placeLabel(@NotNull Rectangle bar, int labelWidth, int trackWidth) {
        int gap = JBUI.scale(LABEL_GAP);
        if (bar.x + bar.width + gap + labelWidth <= trackWidth) return LabelPlacement.AFTER;
        if (labelWidth + 2 * gap <= bar.width) return LabelPlacement.INSIDE;
        return LabelPlacement.NONE;
    }

    /**
     * The header of the Timeline column: the tick labels at their positions.
     */
    @NotNull
    static TableCellRenderer header(@NotNull ChildJobsTimeline table) {
        return new TickHeader(table);
    }

    private void paintBar(@NotNull Graphics2D g2, @NotNull BigQueryChildJob child) {
        StatementTimeline.Bar bar = table.statementTimeline().bar(child);
        if (bar == null) return;
        Rectangle bounds = barBounds(bar, getWidth(), getHeight());
        boolean failed = FAILED.equals(child.status());
        GraphicsUtil.setupAAPainting(g2);
        g2.setColor(failed ? (selected ? FAILED_BAR_SELECTED : FAILED_BAR) : (selected ? BAR_SELECTED : BAR));
        int arc = JBUI.scale(BAR_ARC);
        g2.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, arc, arc);
        paintLabel(g2, RunText.barLabel(child), bounds);
    }

    private void paintLabel(@NotNull Graphics2D g2, @NotNull String label, @NotNull Rectangle bar) {
        if (label.isEmpty()) return;
        UISettings.setupAntialiasing(g2);
        g2.setFont(JBFont.medium());
        FontMetrics after = g2.getFontMetrics();
        int gap = JBUI.scale(LABEL_GAP);
        switch (placeLabel(bar, after.stringWidth(label), getWidth())) {
            case AFTER -> {
                g2.setColor(selected ? RenderingUtil.getSelectionForeground(table) : grayed());
                g2.drawString(label, bar.x + bar.width + gap, baseline(after, bar));
            }
            case INSIDE -> {
                g2.setFont(JBFont.small().asBold());
                FontMetrics inside = g2.getFontMetrics();
                g2.setColor(selected ? LABEL_INSIDE_SELECTED : LABEL_INSIDE);
                g2.drawString(label, bar.x + bar.width - gap - inside.stringWidth(label), baseline(inside, bar));
            }
            case NONE -> {
            }
        }
    }

    private static int baseline(@NotNull FontMetrics metrics, @NotNull Rectangle bar) {
        return bar.y + (bar.height - metrics.getHeight()) / 2 + metrics.getAscent();
    }

    private static void paintGrid(@NotNull Graphics2D g2, @NotNull StatementTimeline timeline, int width, int height) {
        g2.setColor(JBUI.CurrentTheme.Separator.color());
        for (StatementTimeline.Tick tick : timeline.ticks()) {
            int x = Math.min(width - 1, (int) Math.round(tick.at() * width));
            g2.drawLine(x, 0, x, height);
        }
    }

    @NotNull
    private static Color grayed() {
        return SimpleTextAttributes.GRAYED_ATTRIBUTES.getFgColor();
    }

    private static final class TickHeader extends JComponent implements TableCellRenderer {

        private final ChildJobsTimeline table;

        private TickHeader(@NotNull ChildJobsTimeline table) {
            this.table = table;
            setOpaque(false);
        }

        @Override
        public Component getTableCellRendererComponent(JTable jTable, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            return this;
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(0, JBUI.scale(HEADER_HEIGHT));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                UISettings.setupAntialiasing(g2);
                g2.setFont(JBFont.small());
                g2.setColor(grayed());
                FontMetrics metrics = g2.getFontMetrics();
                int baseline = (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent();
                for (StatementTimeline.Tick tick : table.statementTimeline().ticks()) {
                    int labelWidth = metrics.stringWidth(tick.label());
                    int x = Math.max(0, Math.min(getWidth() - labelWidth, (int) Math.round(tick.at() * getWidth())));
                    g2.drawString(tick.label(), x, baseline);
                }
            } finally {
                g2.dispose();
            }
        }
    }
}
