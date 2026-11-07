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
package io.github.rejeb.dataform.language.ui;

import com.intellij.ui.components.JBTabbedPane;
import com.intellij.util.ui.JBUI;

import javax.swing.UIDefaults;
import javax.swing.UIManager;
import javax.swing.plaf.TabbedPaneUI;
import java.awt.Component;
import java.awt.Graphics;

/**
 * Underline tabs that paint no box around their content, as the small tabs of the UI prototype: the content
 * area shows what is behind the tabs, a separator line runs under the tabs and the tab content keeps its own
 * border.
 */
public class TransparentTabbedPane extends JBTabbedPane {

    private static final String CONTENT_OPAQUE = "TabbedPane.contentOpaque";

    /**
     * Creates the tabs, with the tab strip on top.
     */
    public TransparentTabbedPane() {
        setOpaque(false);
        setTabComponentInsets(null);
    }

    /**
     * Installs the look-and-feel tabs with the content area left unfilled, without changing the
     * look-and-feel default for the other tabs.
     */
    @Override
    public void setUI(TabbedPaneUI ui) {
        UIDefaults defaults = UIManager.getDefaults();
        Object contentOpaque = defaults.get(CONTENT_OPAQUE);
        defaults.put(CONTENT_OPAQUE, Boolean.FALSE);
        try {
            super.setUI(ui);
        } finally {
            defaults.put(CONTENT_OPAQUE, contentOpaque);
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        Component selected = getSelectedComponent();
        if (selected != null) {
            int thickness = JBUI.scale(1);
            g.setColor(JBUI.CurrentTheme.Separator.color());
            g.fillRect(0, selected.getY() - thickness, getWidth(), thickness);
        }
        super.paintComponent(g);
    }
}
