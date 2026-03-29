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

import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.JBUI;
import icons.DatabaseIcons;
import io.github.rejeb.dataform.language.fileEditor.GraphTarget;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Optional;

public class LineageNode extends JPanel {

    public static final int NODE_H = 32;
    private static final int MIN_W = 120;
    private static final int MAX_W = 480;
    private static final int PAD_L = 8;
    private static final int PAD_R = 12;

    private static final Color NODE_SRC_BG = new JBColor(new Color(245, 238, 215), new Color(50, 52, 65));
    private static final Color NODE_TGT_BG = new JBColor(new Color(210, 225, 245), new Color(35, 55, 88));
    private static final Color NODE_DEP_BG = new JBColor(new Color(215, 240, 220), new Color(36, 54, 42));
    private static final Color NODE_SRC_BDR = new JBColor(new Color(160, 120, 40), new Color(75, 80, 105));
    private static final Color NODE_TGT_BDR = new JBColor(new Color(30, 90, 180), new Color(60, 130, 220));
    private static final Color NODE_DEP_BDR = new JBColor(new Color(40, 140, 70), new Color(70, 170, 95));
    private static final Color NODE_TEXT = new JBColor(new Color(50, 50, 60), new Color(200, 205, 215));

    private static final Color HOVER_OVERLAY = new Color(255, 255, 255, 18);


    private final LineageNodeType type;
    private final GraphTarget graphTarget;
    private boolean hovered = false;
    private final SimpleColoredComponent label;

    public LineageNode(GraphTarget graphTarget, LineageNodeType type, Project project) {
        super(new BorderLayout());
        this.graphTarget = graphTarget;
        this.type = type;
        setOpaque(false);
        setBorder(JBUI.Borders.empty(0, PAD_L, 0, PAD_R));

        this.label = new SimpleColoredComponent();
        label.setOpaque(false);
        label.setFont(JBUI.Fonts.label(11f));
        label.setIcon(resolveIcon(type, graphTarget.type()));
        label.append(
                graphTarget.name(),
                new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, NODE_TEXT)
        );
        label.append("  " + graphTarget.type(),
                new SimpleTextAttributes(
                        SimpleTextAttributes.STYLE_SMALLER,
                        JBUI.CurrentTheme.ContextHelp.FOREGROUND
                ));
        add(label, BorderLayout.CENTER);

        setToolTipText(graphTarget.fullName());

        boolean hasFile = graphTarget.fileName() != null && !graphTarget.fileName().isBlank();
        if (hasFile) setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                hovered = true;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hovered = false;
                repaint();
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (hasFile) openFile(project);
            }
        });
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension inner = label.getPreferredSize();
        int w = PAD_L + inner.width + PAD_R;
        return new Dimension(Math.max(MIN_W, Math.min(MAX_W, w)), NODE_H);
    }

    @Override
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        Color bg = switch (type) {
            case SOURCE -> NODE_SRC_BG;
            case TARGET -> NODE_TGT_BG;
            case DEPENDENT -> NODE_DEP_BG;
        };
        Color bdr = switch (type) {
            case SOURCE -> NODE_SRC_BDR;
            case TARGET -> NODE_TGT_BDR;
            case DEPENDENT -> NODE_DEP_BDR;
        };

        g2.setColor(new Color(0, 0, 0, 25));
        g2.fillRoundRect(2, 2, w - 1, NODE_H - 1, 8, 8);

        g2.setColor(bg);
        g2.fillRoundRect(0, 0, w - 1, NODE_H - 1, 8, 8);

        if (hovered) {
            g2.setColor(HOVER_OVERLAY);
            g2.fillRoundRect(0, 0, w - 1, NODE_H - 1, 8, 8);
        }

        g2.setColor(hovered ? bdr.brighter() : bdr);
        g2.setStroke(new BasicStroke(type == LineageNodeType.TARGET ? 1.5f : 1f));
        g2.drawRoundRect(0, 0, w - 2, NODE_H - 2, 8, 8);

        g2.dispose();
        super.paintComponent(g);
    }


    private static Icon resolveIcon(LineageNodeType type, String graphTargetType) {
        return switch (type) {
            case SOURCE -> IconUtil.colorize(resolveIcon(graphTargetType),
                    new JBColor(new Color(180, 120, 20), new Color(230, 170, 50)));
            case TARGET -> IconUtil.colorize(resolveIcon(graphTargetType),
                    new JBColor(new Color(30, 90, 180), new Color(60, 130, 220)));
            case DEPENDENT -> IconUtil.colorize(resolveIcon(graphTargetType),
                    new JBColor(new Color(40, 140, 70), new Color(80, 190, 110)));
        };
    }

    private void openFile(Project project) {
        String absolutePath = project.getBasePath() + "/" + graphTarget.fileName();
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            VirtualFile vf = LocalFileSystem.getInstance()
                    .refreshAndFindFileByPath(absolutePath);
            if (vf == null) return;
            ApplicationManager.getApplication().invokeLater(() ->
                    FileEditorManager.getInstance(project)
                            .openEditor(new OpenFileDescriptor(project, vf), true)
            );
        });
    }

    private static Icon resolveIcon(String dataformType) {
        return switch (Optional.ofNullable(dataformType).map(String::toLowerCase).orElse("unknown")) {
            case "table" -> AllIcons.Nodes.DataTables;
            case "view" -> DatabaseIcons.VirtualView;
            case "materialized_view" -> DatabaseIcons.MaterializedView;
            case "assertion" -> AllIcons.Nodes.JunitTestMark;
            case "operation" -> AllIcons.Nodes.Function;
            case "incremental" -> DatabaseIcons.PartionTable;
            case "declaration" -> DatabaseIcons.Foreign_table;
            default -> AllIcons.Nodes.Unknown;
        };
    }
}
