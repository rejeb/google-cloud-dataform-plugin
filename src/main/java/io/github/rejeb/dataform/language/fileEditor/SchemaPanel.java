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
package io.github.rejeb.dataform.language.fileEditor;

import com.intellij.openapi.project.Project;
import io.github.rejeb.dataform.language.schema.sql.DataformTableSchemaService;
import io.github.rejeb.dataform.language.schema.sql.model.DataformDasTable;

import java.util.List;
import java.util.Map;

public class SchemaPanel extends SectionsPreviewPanel {

    private final Project project;

    public SchemaPanel(Project project) {
        this.project = project;
    }

    public void setContent(List<GraphTarget> tables) {
        sectionsPanel.removeAll();
        if (tables != null && !tables.isEmpty()) {
            Map<String, DataformDasTable> allTables = DataformTableSchemaService.getInstance(project).getAllTables();
            for (GraphTarget q : tables) {
                DataformDasTable table = q.fullName() != null ? allTables.get(q.fullName()) : null;
                sectionsPanel.add(new TableSchemaSection(String.format("%s: %s", q.type(), q.name()),
                        table != null ? table.getColumns() : null));
            }
        }
        sectionsPanel.revalidate();
        sectionsPanel.repaint();
    }
}