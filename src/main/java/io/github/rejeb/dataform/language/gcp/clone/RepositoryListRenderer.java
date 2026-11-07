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
package io.github.rejeb.dataform.language.gcp.clone;

import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import io.github.rejeb.dataform.language.gcp.workspace.DataformRepository;

import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;

/**
 * Draws a repository as its name, with its Git remote pushed to the right.
 */
final class RepositoryListRenderer implements ListCellRenderer<DataformRepository> {

    private static final String NO_REMOTE = "No Git remote";

    @Override
    public Component getListCellRendererComponent(JList<? extends DataformRepository> list, DataformRepository value,
                                                  int index, boolean selected, boolean focused) {
        Color foreground = UIUtil.getListForeground(selected, focused);
        JBLabel name = new JBLabel(value.repositoryId());
        name.setForeground(foreground);
        JBLabel detail = new JBLabel(value.hasGitRemote() ? value.remoteDisplayName() : NO_REMOTE);
        detail.setFont(JBFont.medium());
        detail.setForeground(selected ? foreground : UIUtil.getContextHelpForeground());
        JPanel row = new JPanel(new BorderLayout(JBUI.scale(16), 0));
        row.setBackground(UIUtil.getListBackground(selected, focused));
        row.setBorder(JBUI.Borders.empty(6, 8));
        row.add(name, BorderLayout.WEST);
        row.add(detail, BorderLayout.EAST);
        return row;
    }
}
