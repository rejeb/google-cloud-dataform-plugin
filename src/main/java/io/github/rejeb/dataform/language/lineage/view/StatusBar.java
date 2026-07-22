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
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import io.github.rejeb.dataform.language.lineage.graph.LineageNode;
import io.github.rejeb.dataform.language.lineage.model.LineageModel;
import org.jetbrains.annotations.NotNull;

import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.Set;

/**
 * Bottom status row: visible/total node count, visible edge count, layout direction,
 * the selected node's qualified name (right-aligned), and the current zoom level.
 */
public final class StatusBar extends JPanel {

    private final LineageModel model;
    private final JBLabel left = new JBLabel();
    private final JBLabel right = new JBLabel();
    private int zoomPercent = 100;

    public StatusBar(@NotNull LineageModel model) {
        super(new BorderLayout());
        this.model = model;
        setBorder(JBUI.Borders.compound(
                JBUI.Borders.customLine(UIUtil.getBoundsColor(), 1, 0, 0, 0),
                JBUI.Borders.empty(2, 8)));
        setPreferredSize(new Dimension(0, JBUIScale.scale(22)));
        left.setForeground(UIUtil.getLabelDisabledForeground());
        right.setForeground(UIUtil.getLabelDisabledForeground());
        add(left, BorderLayout.WEST);
        add(right, BorderLayout.EAST);

        refresh();
        model.addListener(m -> refresh());
    }

    public void setZoom(int percent) {
        this.zoomPercent = percent;
        refresh();
    }

    private void refresh() {
        Set<String> visible = model.visibleIds();
        int total = model.graph().nodes().size();
        int edges = countVisibleEdges(visible);

        left.setText(visible.size() + "/" + total + " nodes  ·  "
                + edges + " edges  ·  " + model.direction());

        String selected = "";
        if (model.selectedId() != null) {
            LineageNode node = model.graph().node(model.selectedId());
            if (node != null) selected = node.fullName() + "   ";
        }
        right.setText(selected + zoomPercent + "%");
    }

    private int countVisibleEdges(@NotNull Set<String> visible) {
        int count = 0;
        for (LineageNode node : model.graph().nodes()) {
            if (!visible.contains(node.id())) continue;
            for (String pred : model.graph().predecessors(node.id())) {
                if (visible.contains(pred)) count++;
            }
        }
        return count;
    }
}
