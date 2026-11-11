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

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;

import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;

public class PreviewHeaderTest extends BasePlatformTestCase {

    public void testSummaryIsSmallAndGrayed() {
        PreviewHeader header = new PreviewHeader();
        header.setSummary("Direct dependencies of a.sqlx");
        assertEquals("Direct dependencies of a.sqlx", header.summaryText());
        assertEquals(JBFont.medium().getSize(), header.summaryLabel().getFont().getSize());
        assertEquals(UIUtil.getContextHelpForeground(), header.summaryLabel().getForeground());
    }

    public void testTrailingComponentSitsAtTheRight() {
        PreviewHeader header = new PreviewHeader();
        JLabel trailing = new JLabel("x");
        header.setTrailing(trailing);
        header.setSize(600, 36);
        header.doLayout();

        assertEquals(header.getWidth() - header.getInsets().right, trailing.getX() + trailing.getWidth());
    }

    private static final String LONG_SUMMARY = "Direct dependencies of gold_customer_purchase_summary.sqlx";

    private static PreviewHeader narrowHeader(int width, JLabel trailing, JLabel extra) {
        PreviewHeader header = new PreviewHeader();
        header.setSummary(LONG_SUMMARY);
        header.addLeading(extra);
        header.setTrailing(trailing);
        header.setSize(width, 36);
        header.doLayout();
        for (Component child : header.getComponents()) {
            if (child instanceof Container container) {
                container.doLayout();
            }
        }
        return header;
    }

    private static Rectangle inHeader(PreviewHeader header, Component component) {
        return SwingUtilities.convertRectangle(component.getParent(), component.getBounds(), header);
    }

    public void testANarrowHeaderShortensTheSummaryInsteadOfOverlappingTheTrailingComponent() {
        JLabel trailing = new JLabel("Layout direction   Open full lineage");
        JLabel extra = new JLabel("last compile failed");
        PreviewHeader header = narrowHeader(trailing.getPreferredSize().width + JBUI.scale(260), trailing, extra);

        Rectangle summary = inHeader(header, header.summaryLabel());
        Rectangle link = inHeader(header, extra);
        Rectangle toolbar = inHeader(header, trailing);
        assertEquals(trailing.getPreferredSize().width, toolbar.width);
        assertTrue("the summary must be shortened", summary.width < header.summaryLabel().getPreferredSize().width);
        assertEquals("the link keeps its width while the summary can shrink", extra.getPreferredSize().width, link.width);
        assertTrue("the summary must end before the link", summary.x + summary.width <= link.x);
        assertTrue("a gap must stay before the trailing component", link.x + link.width + JBUI.scale(12) <= toolbar.x);
    }

    public void testAShortenedSummaryShowsItsWholeTextInATooltip() {
        JLabel trailing = new JLabel("Layout direction   Open full lineage");
        PreviewHeader narrow = narrowHeader(trailing.getPreferredSize().width + JBUI.scale(200), trailing, new JLabel());
        PreviewHeader wide = narrowHeader(1600, new JLabel("x"), new JLabel());

        assertEquals(LONG_SUMMARY, narrow.summaryLabel().getToolTipText(mouseOver(narrow.summaryLabel())));
        assertNull(wide.summaryLabel().getToolTipText(mouseOver(wide.summaryLabel())));
    }

    public void testOnceTheSummaryIsAtItsMinimumTheLinkShrinksInsteadOfRunningUnderTheTrailingComponent() {
        JLabel trailing = new JLabel("≈ €0.0002 · 12.4 MB");
        JLabel extra = new JLabel("last compile failed");
        PreviewHeader header = narrowHeader(trailing.getPreferredSize().width + JBUI.scale(150), trailing, extra);

        Rectangle link = inHeader(header, extra);
        Rectangle toolbar = inHeader(header, trailing);
        assertTrue("the link must be shortened", link.width < extra.getPreferredSize().width);
        assertTrue("a gap must stay before the trailing component", link.x + link.width + JBUI.scale(12) <= toolbar.x);
    }

    public void testAHiddenLinkLeavesItsWidthToTheSummary() {
        JLabel trailing = new JLabel("Layout direction   Open full lineage");
        int width = trailing.getPreferredSize().width + JBUI.scale(200);
        JLabel hidden = new JLabel("last compile failed");
        hidden.setVisible(false);
        PreviewHeader withHiddenLink = narrowHeader(width, trailing, hidden);
        PreviewHeader withoutLink = narrowHeader(width, new JLabel("Layout direction   Open full lineage"), new JLabel());

        assertEquals(withoutLink.summaryLabel().getWidth(), withHiddenLink.summaryLabel().getWidth());
    }

    public void testASecondTrailingComponentReplacesTheFirst() {
        PreviewHeader header = new PreviewHeader();
        JLabel first = new JLabel("first");
        JLabel second = new JLabel("second");
        header.setTrailing(first);
        header.setTrailing(second);
        header.setSize(600, 36);
        header.doLayout();

        assertNull(first.getParent());
        assertSame(header, second.getParent());
        assertEquals(header.getWidth() - header.getInsets().right, second.getX() + second.getWidth());
    }

    private static MouseEvent mouseOver(JLabel label) {
        return new MouseEvent(label, MouseEvent.MOUSE_MOVED, 0, 0, 1, 1, 0, false);
    }
}
