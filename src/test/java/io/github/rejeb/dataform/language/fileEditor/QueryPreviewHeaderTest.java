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

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

public class QueryPreviewHeaderTest extends BasePlatformTestCase {

    public void testTheHeaderIsAtLeast36PixelsHighWithASeparatorBelow() {
        QueryPreviewHeader header = new QueryPreviewHeader(new QueryCostTotalBar(() -> {
        }), () -> {
        });
        header.showSummary(2, -1, false);
        int height = header.getPreferredSize().height;
        header.setSize(400, height);
        BufferedImage image = new BufferedImage(400, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            header.paint(g);
        } finally {
            g.dispose();
        }

        assertTrue(height >= JBUI.scale(36));
        assertEquals(JBUI.CurrentTheme.Separator.color().getRGB(), image.getRGB(200, height - 1));
        assertEquals(JBUI.scale(12), header.getInsets().left);
    }

    public void testTheSummaryIsGrayedAndOnePointSmaller() {
        QueryPreviewHeader header = new QueryPreviewHeader(new QueryCostTotalBar(() -> {
        }), () -> {
        });

        header.showSummary(3, -1, false);

        assertEquals("3 actions", header.summaryText());
        assertEquals(UIUtil.getContextHelpForeground(), header.summaryLabel().getForeground());
        assertEquals(JBFont.medium().getSize(), header.summaryLabel().getFont().getSize());
    }

    public void testTheFailedCompileLinkShowsTheBuild() {
        boolean[] shown = {false};
        QueryPreviewHeader header = new QueryPreviewHeader(new QueryCostTotalBar(() -> {
        }), () -> shown[0] = true);

        header.showSummary(1, -1, true);
        header.compileFailedLink().doClick();

        assertTrue(shown[0]);
    }
}
