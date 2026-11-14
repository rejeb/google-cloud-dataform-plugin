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
package io.github.rejeb.dataform.language.lineage.view;

import com.intellij.ui.components.JBLabel;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import io.github.rejeb.dataform.language.lineage.dataplex.DataplexGraphBuilder;
import io.github.rejeb.dataform.language.lineage.dataplex.DataplexGraphLoader;
import io.github.rejeb.dataform.language.lineage.dataplex.DataplexView;
import io.github.rejeb.dataform.language.lineage.model.LineageModel;
import io.github.rejeb.dataform.language.lineage.model.LineageSource;
import io.github.rejeb.dataform.language.ui.UiText;
import org.jetbrains.annotations.NotNull;

import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Bottom status row. In the project view: how many of the nodes are shown, whether one is
 * selected, and the zoom level on the right; with the recorded lineage, how many nodes are outside
 * the repository and where and when the lineage was read. In the file view: how many nodes are shown and how to
 * open one.
 */
public final class StatusBar extends JPanel {

    private static final int HEIGHT = 24;
    private static final String FILE_HINT = "double-click a node to open its file";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter DAY_AND_TIME = DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.ENGLISH);

    private final LineageModel model;
    private final boolean fileView;
    private final Clock clock;
    private final JBLabel left = new JBLabel();
    private final JBLabel right = new JBLabel();
    private int zoomPercent = 100;

    public StatusBar(@NotNull LineageModel model) {
        this(model, false, Clock.systemDefaultZone());
    }

    /**
     * @param clock tells whether the recorded lineage was loaded today, and the zone its time is shown in
     */
    StatusBar(@NotNull LineageModel model, @NotNull Clock clock) {
        this(model, false, clock);
    }

    private StatusBar(@NotNull LineageModel model, boolean fileView, @NotNull Clock clock) {
        super(new BorderLayout());
        this.model = model;
        this.fileView = fileView;
        this.clock = clock;
        setBackground(UIUtil.getPanelBackground());
        setBorder(JBUI.Borders.compound(
                JBUI.Borders.customLine(UIUtil.getBoundsColor(), 1, 0, 0, 0),
                JBUI.Borders.empty(0, 10)));
        setPreferredSize(new Dimension(0, JBUIScale.scale(HEIGHT)));
        for (JBLabel label : new JBLabel[]{left, right}) {
            label.setFont(JBFont.medium());
            label.setForeground(UIUtil.getContextHelpForeground());
        }
        add(left, BorderLayout.WEST);
        add(right, BorderLayout.EAST);

        refresh();
        model.addListener(m -> refresh());
    }

    /**
     * The status row of the lineage tab of a file: the number of nodes shown and how to open one.
     */
    public static @NotNull StatusBar forFile(@NotNull LineageModel model) {
        return new StatusBar(model, true, Clock.systemDefaultZone());
    }

    public void setZoom(int percent) {
        this.zoomPercent = percent;
        refresh();
    }

    private void refresh() {
        Set<String> visible = model.visibleIds();
        if (fileView) {
            left.setText(UiText.dot(visible.size() == 1 ? "1 node" : visible.size() + " nodes", FILE_HINT));
            right.setText("");
            return;
        }
        if (model.source() == LineageSource.DATAPLEX) {
            refreshRecorded(visible);
            return;
        }
        int total = model.graph().nodes().size();
        String shown = visible.size() + " of " + total + " nodes";
        left.setText(model.selectedId() != null ? UiText.dot(shown, "1 selected") : shown);
        right.setText(zoomPercent + "%");
    }

    private void refreshRecorded(@NotNull Set<String> visible) {
        DataplexView view = model.dataplexView();
        List<String> parts = new ArrayList<>();
        parts.add(visible.size() + " nodes");
        if (view != null) parts.add(outside(visible) + " outside this repository");
        if (model.selectedId() != null) parts.add("1 selected");
        if (view != null && view.snapshot().truncated()) parts.add("stopped at " + DataplexGraphLoader.MAX_ENTITIES + " nodes");
        left.setText(UiText.dot(parts.toArray(String[]::new)));
        if (view == null) {
            right.setText(UiText.dot(LineageSource.DATAPLEX.label(), zoomPercent + "%"));
            return;
        }
        List<String> where = new ArrayList<>(List.of(LineageSource.DATAPLEX.label(), view.scope().projectId(),
                view.scope().location(), view.range().label().toLowerCase(Locale.ROOT),
                "loaded " + loadedAt(view.snapshot().loadedAt())));
        switch (model.recordedActivity()) {
            case CHECKING -> where.add("checking…");
            case UPDATING -> where.add("updating…");
            default -> {
            }
        }
        where.add(zoomPercent + "%");
        right.setText(UiText.dot(where.toArray(String[]::new)));
    }

    private @NotNull String loadedAt(@NotNull Instant loaded) {
        ZonedDateTime time = loaded.atZone(clock.getZone());
        return (time.toLocalDate().equals(LocalDate.now(clock)) ? TIME : DAY_AND_TIME).format(time);
    }

    private long outside(@NotNull Set<String> visible) {
        return visible.stream().map(model.graph()::node)
                .filter(node -> node != null && (DataplexGraphBuilder.TYPE_OUTSIDE.equals(node.dataformType())
                        || DataplexGraphBuilder.TYPE_ASSET.equals(node.dataformType())))
                .count();
    }
}
