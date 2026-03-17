/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
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
import io.github.rejeb.dataform.language.fileEditor.GraphTarget;


import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class LineageGroup extends JPanel {

    private static final int ARROW_W  = 40;
    private static final int GAP_Y    = 10;
    private static final Color ARROW_CLR = new JBColor(new Color(150, 155, 170), new Color(110, 115, 135));

    private final List<LineageNode> sourceNodes    = new ArrayList<>();
    private final LineageNode       targetNode;
    private final List<LineageNode> dependentNodes = new ArrayList<>();

    public LineageGroup(LineageGraph graph, Project project) {
        setLayout(null);
        setOpaque(false);

        for (GraphTarget dep : graph.dependencies()) {
            LineageNode n = new LineageNode(dep, LineageNodeType.SOURCE, project);
            sourceNodes.add(n);
            add(n);
        }

        targetNode = new LineageNode(graph.targetTable(), LineageNodeType.TARGET, project);
        add(targetNode);

        for (GraphTarget dep : graph.dependents()) {
            LineageNode n = new LineageNode(dep, LineageNodeType.DEPENDENT, project);
            dependentNodes.add(n);
            add(n);
        }
    }

    @Override
    public Dimension getPreferredSize() {
        int depsSection   = sourceNodes.isEmpty()    ? 0 : maxPrefWidth(sourceNodes)   + ARROW_W;
        int tgtW          = targetNode.getPreferredSize().width;
        int depntsSection = dependentNodes.isEmpty() ? 0 : ARROW_W + maxPrefWidth(dependentNodes);
        int totalW = depsSection + tgtW + depntsSection;
        int totalH = computeGroupHeight();
        return new Dimension(totalW, totalH);
    }

    @Override public Dimension getMinimumSize() { return getPreferredSize(); }
    @Override public Dimension getMaximumSize() { return getPreferredSize(); }

    @Override
    public void doLayout() {
        int totalH    = computeGroupHeight();
        int tgtW      = targetNode.getPreferredSize().width;
        int depsW     = sourceNodes.isEmpty()    ? 0 : maxPrefWidth(sourceNodes);
        int depntsW   = dependentNodes.isEmpty() ? 0 : maxPrefWidth(dependentNodes);
        int tgtX      = depsW + (sourceNodes.isEmpty() ? 0 : ARROW_W);
        int depntsX   = tgtX + tgtW + (dependentNodes.isEmpty() ? 0 : ARROW_W);

        if (!sourceNodes.isEmpty()) {
            int leftH  = sourceNodes.size() * LineageNode.NODE_H + (sourceNodes.size() - 1) * GAP_Y;
            int leftY0 = (totalH - leftH) / 2;
            for (int i = 0; i < sourceNodes.size(); i++) {
                sourceNodes.get(i).setBounds(0, leftY0 + i * (LineageNode.NODE_H + GAP_Y), depsW, LineageNode.NODE_H);
            }
        }

        targetNode.setBounds(tgtX, (totalH - LineageNode.NODE_H) / 2, tgtW, LineageNode.NODE_H);

        if (!dependentNodes.isEmpty()) {
            int rightH  = dependentNodes.size() * LineageNode.NODE_H + (dependentNodes.size() - 1) * GAP_Y;
            int rightY0 = (totalH - rightH) / 2;
            for (int i = 0; i < dependentNodes.size(); i++) {
                dependentNodes.get(i).setBounds(depntsX, rightY0 + i * (LineageNode.NODE_H + GAP_Y), depntsW, LineageNode.NODE_H);
            }
        }
    }

    @Override
    public void paint(Graphics g) {
        super.paint(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(ARROW_CLR);
        g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        Rectangle tgt   = targetNode.getBounds();
        int       tgtCY = tgt.y + tgt.height / 2;

        for (LineageNode n : sourceNodes) {
            Rectangle b = n.getBounds();
            drawArrow(g2, b.x + b.width, b.y + b.height / 2, tgt.x, tgtCY);
        }

        for (LineageNode n : dependentNodes) {
            Rectangle b = n.getBounds();
            drawArrow(g2, tgt.x + tgt.width, tgtCY, b.x, b.y + b.height / 2);
        }

        g2.dispose();
    }

    private void drawArrow(Graphics2D g2, int x1, int y1, int x2, int y2) {
        int mx = x1 + (x2 - x1) / 2;
        if (Math.abs(y1 - y2) < 4) {
            g2.drawLine(x1, y1, x2, y2);
        } else {
            g2.drawLine(x1, y1, mx, y1);
            g2.drawLine(mx, y1, mx, y2);
            g2.drawLine(mx, y2, x2, y2);
        }
        int s = 6;
        g2.fill(new Polygon(
                new int[]{x2, x2 - s, x2 - s},
                new int[]{y2, y2 - s/2, y2 + s/2},
                3
        ));
    }

    private int computeGroupHeight() {
        int leftRows  = Math.max(1, sourceNodes.size());
        int rightRows = Math.max(1, dependentNodes.size());
        int maxRows   = Math.max(leftRows, rightRows);
        return maxRows * LineageNode.NODE_H + (maxRows - 1) * GAP_Y;
    }

    private int maxPrefWidth(List<LineageNode> nodes) {
        return nodes.stream().mapToInt(n -> n.getPreferredSize().width).max().orElse(0);
    }
}
