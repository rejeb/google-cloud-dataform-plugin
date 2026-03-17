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

import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import io.github.rejeb.dataform.language.compilation.model.CompiledQuery;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class QueryPanel extends JPanel {

    private final Project project;
    private final FileType fileType;
    private final JPanel sectionsPanel;
    private final List<TableQuerySection> sections = new ArrayList<>();

    public QueryPanel(Project project, FileType fileType) {
        super(new BorderLayout());
        this.project  = project;
        this.fileType = fileType;
        setOpaque(true);
        setBackground(UIUtil.getPanelBackground());

        sectionsPanel = new JPanel();
        sectionsPanel.setLayout(new BoxLayout(sectionsPanel, BoxLayout.Y_AXIS));
        sectionsPanel.setOpaque(false);
        sectionsPanel.setBorder(JBUI.Borders.empty(8, 10));

        JBScrollPane scroll = new JBScrollPane(sectionsPanel);
        scroll.setBorder(JBUI.Borders.empty());
        add(scroll, BorderLayout.CENTER);
    }

    public void setContent(List<FormattedCompiledQuery> queries) {
        sections.forEach(TableQuerySection::dispose);
        sections.clear();
        sectionsPanel.removeAll();

        if (queries == null || queries.isEmpty()) {
            sectionsPanel.revalidate();
            sectionsPanel.repaint();
            return;
        }

        for (FormattedCompiledQuery q : queries) {
            TableQuerySection section = new TableQuerySection(q, fileType, project);
            sections.add(section);
            sectionsPanel.add(section);
        }

        sectionsPanel.revalidate();
        sectionsPanel.repaint();
    }


    public EditorEx getEditor() {
        if (sections.isEmpty()) return null;
        return sections.get(0).getQuerySection().getEditor();
    }

    public void dispose() {
        sections.forEach(TableQuerySection::dispose);
        sections.clear();
    }

    /**
     * Returns true if at least one compiled query with a non-null query body is available.
     */
    public boolean hasQuery() {
        return sections.stream().anyMatch(s -> s.getQuerySection().hasContent());
    }

    /**
     * Returns the current list of compiled queries.
     */
    public List<FormattedCompiledQuery> getCompiledQueries() {
        return sections.stream()
                .map(TableQuerySection::getQuery)
                .toList();
    }

}
