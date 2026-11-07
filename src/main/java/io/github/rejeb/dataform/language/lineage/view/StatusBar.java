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
import io.github.rejeb.dataform.language.lineage.model.LineageModel;
import io.github.rejeb.dataform.language.ui.UiText;
import org.jetbrains.annotations.NotNull;

import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.Set;

/**
 * Bottom status row. In the project view: how many of the nodes are shown, whether one is
 * selected, and the zoom level on the right. In the file view: how many nodes are shown and how to
 * open one.
 */
public final class StatusBar extends JPanel {

    private static final int HEIGHT = 24;
    private static final String FILE_HINT = "double-click a node to open its file";

    private final LineageModel model;
    private final boolean fileView;
    private final JBLabel left = new JBLabel();
    private final JBLabel right = new JBLabel();
    private int zoomPercent = 100;

    public StatusBar(@NotNull LineageModel model) {
        this(model, false);
    }

    private StatusBar(@NotNull LineageModel model, boolean fileView) {
        super(new BorderLayout());
        this.model = model;
        this.fileView = fileView;
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
        return new StatusBar(model, true);
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
        int total = model.graph().nodes().size();
        String shown = visible.size() + " of " + total + " nodes";
        left.setText(model.selectedId() != null ? UiText.dot(shown, "1 selected") : shown);
        right.setText(zoomPercent + "%");
    }
}
