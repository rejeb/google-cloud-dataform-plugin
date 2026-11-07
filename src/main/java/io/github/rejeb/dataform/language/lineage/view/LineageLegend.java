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
import com.intellij.ui.components.panels.VerticalLayout;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.SeparatorComponent;
import com.intellij.ui.SeparatorOrientation;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The action types of a lineage graph: the order they are listed in, and the legend that explains
 * the glyphs and colours of the canvas.
 */
final class LineageLegend {

    private static final List<String> CANONICAL_TYPES = List.of(
            "table", "materialized_view", "view", "incremental", "declaration", "assertion", "operation", "test");

    private LineageLegend() {
    }

    /**
     * The given types, the usual Dataform ones first in the order of the UI prototype, then the
     * others.
     */
    static @NotNull List<String> orderedTypes(@NotNull Collection<String> present) {
        Set<String> ordered = new LinkedHashSet<>();
        for (String type : CANONICAL_TYPES) {
            if (present.contains(type)) ordered.add(type);
        }
        present.stream().sorted().forEach(ordered::add);
        return new ArrayList<>(ordered);
    }

    /**
     * The legend: the glyph and name of each type, then what the colours of the edges and outlines
     * mean.
     */
    static @NotNull JComponent panel(@NotNull List<String> types) {
        JPanel panel = new JPanel(new VerticalLayout(JBUIScale.scale(6)));
        panel.setBorder(JBUI.Borders.empty(8, 10));
        panel.add(hint("Node types"));
        for (String type : types) {
            panel.add(new JBLabel(type, TypeGlyphIcon.small(type), JBLabel.LEADING));
        }
        panel.add(new SeparatorComponent(JBUI.CurrentTheme.Separator.color(), SeparatorOrientation.HORIZONTAL));
        panel.add(hint("Blue edges: upstream and downstream of the selected node"));
        panel.add(hint("Yellow outline: search match, or upstream and downstream of the selected node"));
        return panel;
    }

    private static @NotNull JBLabel hint(@NotNull String text) {
        JBLabel label = new JBLabel(text);
        label.setFont(JBFont.medium());
        label.setForeground(UIUtil.getContextHelpForeground());
        return label;
    }
}
