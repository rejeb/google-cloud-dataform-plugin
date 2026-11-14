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
import com.intellij.ui.InlineBanner;
import io.github.rejeb.dataform.language.ui.Banners;

import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;

public class SectionsPreviewPanelTest extends BasePlatformTestCase {

    private static final String LONG_ERROR = "No matching signature for function COALESCE Argument types: FLOAT64, STRING "
            + "Signature: COALESCE([T1, ...]) Unable to find common supertype for templated argument <T1> "
            + "Input types for <T1>: {DOUBLE, STRING} at [12:8]";

    public void testALongBannerDoesNotWidenTheSections() {
        SectionsPreviewPanel panel = new SectionsPreviewPanel() {
        };
        InlineBanner banner = Banners.error("BigQuery dry run failed", LONG_ERROR);
        panel.sectionsPanel.add(banner);
        panel.setSize(400, 600);

        layout(panel);
        layout(panel);

        JViewport viewport = (JViewport) SwingUtilities.getAncestorOfClass(JViewport.class, panel.sectionsPanel);
        JScrollPane scroll = (JScrollPane) viewport.getParent();
        assertEquals(viewport.getWidth(), panel.sectionsPanel.getWidth());
        assertFalse(scroll.getHorizontalScrollBar().isVisible());
        assertTrue("the banner must fit the pane, was " + banner.getWidth(), banner.getWidth() <= viewport.getWidth());
    }

    public void testALongBannerWrapsToShowItsWholeMessage() {
        SectionsPreviewPanel panel = new SectionsPreviewPanel() {
        };
        InlineBanner shortBanner = Banners.error("BigQuery dry run failed", "Syntax error");
        InlineBanner longBanner = Banners.error("BigQuery dry run failed", LONG_ERROR);
        panel.sectionsPanel.add(shortBanner);
        panel.sectionsPanel.add(longBanner);
        panel.setSize(400, 600);

        layout(panel);
        layout(panel);

        assertTrue("the long message must wrap: " + longBanner.getHeight() + " vs " + shortBanner.getHeight(),
                longBanner.getHeight() > shortBanner.getHeight());
        assertTrue("the wrapped message must not be cut: " + longBanner.getHeight() + " < " + longBanner.getPreferredSize().height,
                longBanner.getHeight() >= longBanner.getPreferredSize().height);
    }

    private static void layout(Component component) {
        if (component instanceof Container container) {
            container.doLayout();
            for (Component child : container.getComponents()) layout(child);
        }
    }
}
