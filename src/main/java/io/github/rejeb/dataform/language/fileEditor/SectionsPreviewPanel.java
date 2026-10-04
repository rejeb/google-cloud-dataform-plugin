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

import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;

import javax.swing.BoxLayout;
import javax.swing.JPanel;
import java.awt.BorderLayout;

/**
 * A preview tab showing sections stacked vertically in a scroll pane, on the panel background.
 * Subclasses fill {@link #sectionsPanel} and may add components around the scroll pane.
 */
abstract class SectionsPreviewPanel extends JPanel {

    protected final JPanel sectionsPanel;

    SectionsPreviewPanel() {
        super(new BorderLayout());
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
}
