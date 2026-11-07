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
package io.github.rejeb.dataform.language.lineage.view;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.lineage.model.LineageModel;

public class LineageFocusBannerTest extends BasePlatformTestCase {

    public void testTheBannerShowsOnlyWhileFocused() {
        LineageModel model = LineageViewFixtures.model();
        LineageFocusBanner banner = new LineageFocusBanner(model);
        assertFalse(banner.component().isVisible());

        model.focusOn(LineageViewFixtures.BETA.id());
        assertTrue(banner.component().isVisible());
        assertTrue(banner.message(), banner.message().contains("Focused on <b>b.beta</b>: 1 upstream, 0 downstream"));

        model.exitFocus();
        assertFalse(banner.component().isVisible());
    }

    public void testTheBannerFloatsOverTheCanvas() {
        LineageModel model = LineageViewFixtures.model();
        GraphCanvas canvas = new GraphCanvas(getProject(), model);
        LineageFocusBanner banner = new LineageFocusBanner(model);
        CanvasOverlayPanel overlay = new CanvasOverlayPanel(canvas, banner.view(), null);
        model.focusOn(LineageViewFixtures.BETA.id());
        overlay.setSize(800, 500);
        overlay.doLayout();

        assertEquals(new java.awt.Rectangle(0, 0, 800, 500), canvas.getBounds());
        java.awt.Rectangle bounds = banner.view().getBounds();
        assertEquals(com.intellij.util.ui.JBUI.scale(16), bounds.x);
        assertEquals(com.intellij.util.ui.JBUI.scale(12), bounds.y);
        assertEquals(800 - 2 * com.intellij.util.ui.JBUI.scale(16), bounds.width);
    }
}
