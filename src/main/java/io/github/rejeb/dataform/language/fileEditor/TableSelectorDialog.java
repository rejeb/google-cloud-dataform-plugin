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
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

public class TableSelectorDialog extends DialogWrapper {

    private final List<FormattedCompiledQuery> queries;
    private JBList<String> list;

    public TableSelectorDialog(@NotNull Project project,
                               @NotNull List<FormattedCompiledQuery> queries) {
        super(project, true);
        this.queries = queries;
        setTitle("Select Query to Execute");
        setOKButtonText("Execute");
        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        String[] names = queries.stream()
                .map(FormattedCompiledQuery::tableName)
                .toArray(String[]::new);

        list = new JBList<>(names);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setSelectedIndex(0);
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) doOKAction();
            }
        });

        JBScrollPane scroll = new JBScrollPane(list);
        scroll.setPreferredSize(new Dimension(JBUI.scale(350), JBUI.scale(200)));
        return scroll;
    }

    /**
     * @return la query sélectionnée, ou null si aucune sélection
     */
    public @Nullable FormattedCompiledQuery getSelectedQuery() {
        int idx = list.getSelectedIndex();
        return (idx >= 0) ? queries.get(idx) : null;
    }
}
