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

import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import io.github.rejeb.dataform.language.schema.sql.DataformTableSchemaService;
import io.github.rejeb.dataform.language.schema.sql.model.ColumnInfo;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SchemaPanel extends JPanel {

    private final Project project;
    private final JPanel sectionsPanel;
    private final List<TableSchemaSection> sections = new ArrayList<>();

    public SchemaPanel(Project project) {
        super(new BorderLayout());
        this.project = project;
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

    public void setContent(List<LineageGraph> tables) {
        sections.forEach(TableSchemaSection::dispose);
        sections.clear();
        sectionsPanel.removeAll();

        if (tables == null || tables.isEmpty()) {
            sectionsPanel.revalidate();
            sectionsPanel.repaint();
            return;
        }

        DataformTableSchemaService schemaService = project.getService(DataformTableSchemaService.class);
        Map<String, List<ColumnInfo>> allSchemas = schemaService.getAllSchemas();

        for (LineageGraph q : tables) {
            String tableName = q.targetTable().fullName();
            String displayName = String.format("%s: %s", q.targetTable().type(), q.targetTable().name());
            List<ColumnInfo> schema = tableName != null ? allSchemas.get(tableName) : null;

            TableSchemaSection section = new TableSchemaSection(displayName, schema);
            sections.add(section);
            sectionsPanel.add(section);
        }

        sectionsPanel.revalidate();
        sectionsPanel.repaint();
    }

}