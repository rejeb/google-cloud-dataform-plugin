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
package io.github.rejeb.dataform.language.diagnostics;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

/**
 * An editor banner whose message wraps to as many lines as the editor width requires.
 */
public class WrappingEditorNotificationPanel extends EditorNotificationPanel {

    private JTextArea textArea;

    public WrappingEditorNotificationPanel(@NotNull FileEditor fileEditor, @NotNull String text) {
        super(fileEditor, EditorNotificationPanel.Status.Warning);
        installWrappingText(text);
    }

    public WrappingEditorNotificationPanel(@NotNull Status status, @NotNull String text) {
        super(status);
        installWrappingText(text);
    }

    /**
     * Replaces the banner message, keeping the wrapping behaviour of the initial text.
     */
    public void setWrappingText(@NotNull String text) {
        if (textArea == null) {
            setText(text);
            return;
        }
        textArea.setText(text);
        textArea.revalidate();
        revalidate();
        repaint();
    }

    private void installWrappingText(@NotNull String text) {
        Container parent = myTextLabel.getParent();
        if (parent == null) {
            setText(text);
            return;
        }
        JTextArea area = createArea(text);
        parent.remove(myTextLabel);
        parent.add(area, BorderLayout.CENTER);
        textArea = area;

        area.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                area.revalidate();
                revalidate();
            }
        });
    }

    private JTextArea createArea(@NotNull String text) {
        JTextArea area = new JTextArea(text) {
            @Override
            public Dimension getPreferredSize() {
                Dimension preferred = super.getPreferredSize();
                int width = getWidth();
                if (width <= 0) {
                    return preferred;
                }
                setSize(width, Short.MAX_VALUE);
                Dimension wrapped = super.getPreferredSize();
                return new Dimension(width, wrapped.height);
            }
        };
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setEditable(false);
        area.setFocusable(false);
        area.setOpaque(false);
        area.setBorder(JBUI.Borders.empty());
        area.setFont(myTextLabel.getFont());
        area.setForeground(myTextLabel.getForeground());
        return area;
    }
}
