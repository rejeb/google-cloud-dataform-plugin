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
package io.github.rejeb.dataform.language.fileEditor.lineage;

import com.intellij.openapi.project.Project;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;


import javax.swing.*;
import java.awt.*;
import java.util.List;

public class LineageGraphPanel extends JPanel {

    private static final int   GROUP_GAP    = 28;
    private static final int   PAD          = 18;
    private static final Color DIVIDER_CLR  = new JBColor(new Color(200, 200, 210), new Color(62, 64, 68));
    private final Project project;
    public LineageGraphPanel(Project project) {
        this.project = project;
        setOpaque(false);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(JBUI.Borders.empty(PAD));
    }

    public void setData(List<LineageGraph> graphs) {
        removeAll();
        if (graphs == null || graphs.isEmpty()) {
            revalidate();
            repaint();
            return;
        }

        for (int i = 0; i < graphs.size(); i++) {
            if (i > 0) {
                add(Box.createVerticalStrut(GROUP_GAP / 2));
                add(new GroupDivider());
                add(Box.createVerticalStrut(GROUP_GAP / 2));
            }
            LineageGroup group = new LineageGroup(graphs.get(i), project);
            group.setAlignmentX(Component.LEFT_ALIGNMENT);
            add(group);
        }

        revalidate();
        repaint();
    }

    private class GroupDivider extends JComponent {

        GroupDivider() {
            setOpaque(false);
            setAlignmentX(Component.LEFT_ALIGNMENT);
        }

        @Override
        public Dimension getPreferredSize() { return new Dimension(Short.MAX_VALUE, 1); }
        @Override public Dimension getMinimumSize() { return new Dimension(0, 1); }
        @Override public Dimension getMaximumSize() { return new Dimension(Short.MAX_VALUE, 1); }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setColor(DIVIDER_CLR);
            g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL,
                    0, new float[]{4, 4}, 0));
            g2.drawLine(0, 0, getWidth(), 0);
            g2.dispose();
        }
    }

}
