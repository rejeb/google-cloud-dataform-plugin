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
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.ui.RoundedLineBorder;
import com.intellij.util.ui.JBUI;

import javax.swing.JPanel;
import javax.swing.border.CompoundBorder;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

public class CodeBlockTest extends BasePlatformTestCase {

    private EditorEx editor;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        editor = ReadOnlyEditors.viewer(getProject(), "SELECT 1", PlainTextFileType.INSTANCE);
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            ReadOnlyEditors.release(editor);
        } finally {
            super.tearDown();
        }
    }

    public void testTheBlockHoldsTheEditorInsideARoundedBorder() {
        CodeBlock block = new CodeBlock(editor, new JPanel());

        assertFalse(block.isOpaque());
        assertInstanceOf(block.getBorder(), CompoundBorder.class);
        RoundedLineBorder line = assertInstanceOf(((CompoundBorder) block.getBorder()).getOutsideBorder(),
                RoundedLineBorder.class);
        assertEquals(JBUI.CurrentTheme.Separator.color(), line.getLineColor());
        assertEquals(JBUI.scale(8) + 1, block.getInsets().top);
        assertEquals(JBUI.scale(8) + 1, block.getInsets().bottom);
    }

    public void testTheCornersAreRoundedAndTheInsideTakesTheEditorBackground() {
        JPanel content = new JPanel();
        CodeBlock block = new CodeBlock(editor, content);
        block.setSize(200, 80);
        BufferedImage image = new BufferedImage(200, 80, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            block.paint(g);
        } finally {
            g.dispose();
        }

        assertEquals(0, image.getRGB(0, 0) >>> 24);
        assertEquals(0, image.getRGB(199, 79) >>> 24);
        assertEquals(editor.getBackgroundColor().getRGB(), image.getRGB(100, 4));
    }

    public void testTheContentFillsTheBlock() {
        JPanel content = new JPanel();

        CodeBlock block = new CodeBlock(editor, content);

        assertSame(block, content.getParent());
    }
}
