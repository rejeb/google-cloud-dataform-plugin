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
package io.github.rejeb.dataform.language.fileEditor;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.sql.psi.SqlLanguage;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import io.github.rejeb.dataform.language.compilation.model.CompiledQuery;

import javax.swing.*;
import java.awt.*;

class TableQuerySection extends JPanel {

    private final QuerySection preOpsSection;
    private final QuerySection querySection;
    private final QuerySection postOpsSection;
    private final QuerySection errorsSection;
    private final JPanel contentPanel;
    private boolean expanded = true;
    private final JPanel header;

    TableQuerySection(CompiledQuery compiledQuery, FileType fileType, Project project) {
        super(new BorderLayout());
        setOpaque(false);
        setBorder(JBUI.Borders.emptyBottom(8));

        header = new JPanel(new BorderLayout());
        header.setOpaque(true);
        header.setBackground(UIUtil.getPanelBackground().brighter());
        header.setBorder(JBUI.Borders.empty(5, 8));
        header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel toggleIcon = new JLabel(AllIcons.General.ArrowDown);
        JLabel tableLabel = new JLabel(compiledQuery.tableName() != null ? compiledQuery.tableName() : "Unknown table");
        tableLabel.setFont(JBUI.Fonts.label(12).asBold());
        tableLabel.setForeground(UIUtil.getLabelForeground());
        tableLabel.setBorder(JBUI.Borders.emptyLeft(6));

        header.add(toggleIcon, BorderLayout.WEST);
        header.add(tableLabel, BorderLayout.CENTER);

        preOpsSection = new QuerySection("Pre Operations", fileType, project, false);
        querySection = new QuerySection("Query", fileType, project, false);
        postOpsSection = new QuerySection("Post Operations", fileType, project, false);
        errorsSection = new QuerySection("Compilation Errors", null, project, true);

        contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setOpaque(false);
        contentPanel.setBorder(JBUI.Borders.empty(8, 12, 4, 12));
        contentPanel.add(preOpsSection);
        contentPanel.add(querySection);
        contentPanel.add(postOpsSection);
        contentPanel.add(errorsSection);

        add(header, BorderLayout.NORTH);
        add(contentPanel, BorderLayout.CENTER);

        var formattedPreOps = compiledQuery.preOps().stream().map(query -> formatSql(query, project)).toList();
        var formattedPostOps = compiledQuery.postOps().stream().map(query -> formatSql(query, project)).toList();
        var formattedQuery = compiledQuery.query() != null ? formatSql(compiledQuery.query(), project) : null;
        preOpsSection.setContent(
                !formattedPreOps.isEmpty()
                        ? String.join("\n", formattedPreOps) : null);
        querySection.setContent(formattedQuery);
        postOpsSection.setContent(
                !formattedPostOps.isEmpty()
                        ? String.join("\n", formattedPostOps) : null);
        errorsSection.setContent(
                compiledQuery.compilationErrors() != null && !compiledQuery.compilationErrors().isEmpty()
                        ? String.join("\n", compiledQuery.compilationErrors())
                        : null);

        header.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                expanded = !expanded;
                contentPanel.setVisible(expanded);
                toggleIcon.setIcon(expanded ? AllIcons.General.ArrowDown
                        : AllIcons.General.ArrowRight);
                JComponent parent = (JComponent) getParent();
                if (parent != null) parent.revalidate();
                revalidate();
                repaint();
            }
        });

    }

    @Override
    public Dimension getPreferredSize() {
        if (!expanded) {
            Dimension h = header.getPreferredSize();
            Insets ins = getInsets();
            return new Dimension(super.getPreferredSize().width,
                    h.height + ins.top + ins.bottom);
        }
        return super.getPreferredSize();
    }

    @Override
    public Dimension getMaximumSize() {
        if (!expanded) {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }
        return new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    public QuerySection getQuerySection() {
        return querySection;
    }

    public void dispose() {
        preOpsSection.dispose();
        querySection.dispose();
        postOpsSection.dispose();
        errorsSection.dispose();
    }

    public String formatSql(String sql, Project project) {
        PsiFileFactory factory = PsiFileFactory.getInstance(project);
        PsiFile tempFile = factory.createFileFromText(
                "temp.sql",
                SqlLanguage.INSTANCE,
                sql.replaceAll("\n +", "\n")
        );
        CodeStyleManager.getInstance(project).reformat(tempFile);
        return tempFile.getText();
    }
}
