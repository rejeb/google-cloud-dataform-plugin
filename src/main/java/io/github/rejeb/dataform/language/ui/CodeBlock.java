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

import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.ui.RoundedLineBorder;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.RoundRectangle2D;

/**
 * A read-only editor shown as a code block: on the editor background, inside a thin rounded border,
 * with some room above and below the text.
 */
public final class CodeBlock extends JPanel {

    private static final int ARC = 12;
    private static final int VERTICAL_PADDING = 8;

    private final EditorEx editor;

    /**
     * Wraps the component that shows the editor.
     *
     * @param editor  the editor whose background fills the block
     * @param content the editor component, or a panel holding it
     */
    public CodeBlock(@NotNull EditorEx editor, @NotNull JComponent content) {
        super(new BorderLayout());
        this.editor = editor;
        setOpaque(false);
        setBorder(JBUI.Borders.compound(
                new RoundedLineBorder(JBUI.CurrentTheme.Separator.color(), JBUI.scale(ARC), 1),
                JBUI.Borders.empty(VERTICAL_PADDING, 0)));
        editor.setBorder(JBUI.Borders.empty());
        add(content, BorderLayout.CENTER);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            GraphicsUtil.setupAAPainting(g2);
            g2.setColor(editor.getBackgroundColor());
            int arc = JBUI.scale(ARC);
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), arc, arc));
        } finally {
            g2.dispose();
        }
    }
}
