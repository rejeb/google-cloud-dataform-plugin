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

import com.intellij.icons.AllIcons;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.ui.SeparatorComponent;
import com.intellij.ui.border.CustomLineBorder;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.CompoundBorder;
import javax.swing.text.JTextComponent;
import java.awt.Component;
import java.awt.Container;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

public class CollapsibleSectionTest extends BasePlatformTestCase {

    public void testSubtitleFollowsTheTitle() {
        CollapsibleSection section = new CollapsibleSection("orders", new JPanel());

        section.setSubtitle("table");

        JLabel subtitle = labelWithText(header(section), "table");
        assertNotNull(subtitle);
        assertTrue(subtitle.isVisible());
    }

    public void testNoSubtitleShowsNothing() {
        CollapsibleSection section = new CollapsibleSection("orders", new JPanel());

        section.setSubtitle(null);

        assertTrue(labels(header(section)).stream()
                .noneMatch(label -> label.isVisible() && label.getText() != null && !label.getText().isEmpty()));
    }

    public void testSetExpandedHidesTheContent() {
        JPanel content = new JPanel();
        CollapsibleSection section = new CollapsibleSection("orders", content);

        section.setExpanded(false);

        assertFalse(section.isExpanded());
        assertFalse(content.isVisible());
        section.setExpanded(true);
        assertTrue(section.isExpanded());
        assertTrue(content.isVisible());
    }

    public void testTheHeaderIsTransparentWithoutABottomLine() {
        CollapsibleSection section = new CollapsibleSection("orders", new JPanel());

        JComponent header = header(section);

        assertFalse(header.isOpaque());
        assertFalse(header.getBorder() instanceof CustomLineBorder);
        assertFalse(header.getBorder() instanceof CompoundBorder compound
                && compound.getOutsideBorder() instanceof CustomLineBorder);
        assertEquals(JBUI.scale(32), header.getPreferredSize().height);
    }

    public void testALineRunsFromTheSubtitleToTheTrailingComponents() {
        CollapsibleSection section = new CollapsibleSection("orders", new JPanel());
        section.setSubtitle("table");
        JLabel cost = new JLabel("1 GB");
        section.setTrailing(cost);
        section.setSize(600, 200);
        layOut(section);

        SeparatorComponent line = all(header(section), SeparatorComponent.class).getFirst();
        Rectangle lineBounds = boundsInHeader(section, line);
        Rectangle subtitleBounds = boundsInHeader(section, labelWithText(header(section), "table"));
        Rectangle costBounds = boundsInHeader(section, cost);

        assertTrue(lineBounds.x >= subtitleBounds.x + subtitleBounds.width + JBUI.scale(12));
        assertTrue(lineBounds.x + lineBounds.width <= costBounds.x - JBUI.scale(12));
        assertTrue(lineBounds.width > 100);
        int middle = header(section).getHeight() / 2;
        assertTrue(Math.abs(lineBounds.y - middle) <= 1);
    }

    public void testTheIconStandsBetweenTheChevronAndTheTitle() {
        CollapsibleSection section = new CollapsibleSection("orders", new JPanel());
        section.setIcon(AllIcons.Nodes.Class);
        section.setSize(600, 200);
        layOut(section);

        JLabel icon = labelWithIcon(header(section), AllIcons.Nodes.Class);
        JLabel chevron = labelWithIcon(header(section), AllIcons.General.ChevronDown);

        assertNotNull(icon);
        assertTrue(icon.isVisible());
        assertTrue(boundsInHeader(section, icon).x > boundsInHeader(section, chevron).x);
    }

    public void testNoIconByDefault() {
        CollapsibleSection section = new CollapsibleSection("orders", new JPanel());

        assertTrue(labels(header(section)).stream()
                .filter(label -> label.getIcon() != null && label.isVisible())
                .allMatch(label -> label.getIcon() == AllIcons.General.ChevronDown));
    }

    public void testTheSubtitleIsGrayedAndOnePointSmaller() {
        CollapsibleSection section = new CollapsibleSection("orders", new JPanel());

        section.setSubtitle("table");

        JLabel subtitle = labelWithText(header(section), "table");
        assertEquals(UIUtil.getContextHelpForeground(), subtitle.getForeground());
        assertEquals(JBFont.medium().getSize(), subtitle.getFont().getSize());
    }

    public void testTheContentIsIndentedUnderTheTitle() {
        JPanel content = new JPanel();

        new CollapsibleSection("orders", content);

        assertEquals(JBUI.scale(22), content.getInsets().left);
        assertEquals(0, content.getInsets().top);
    }

    public void testTheChevronFollowsTheState() {
        CollapsibleSection section = new CollapsibleSection("orders", new JPanel());

        assertNotNull(labelWithIcon(header(section), AllIcons.General.ChevronDown));
        section.setExpanded(false);
        assertNotNull(labelWithIcon(header(section), AllIcons.General.ChevronRight));
    }

    private static JComponent header(CollapsibleSection section) {
        return (JComponent) section.getComponent(0);
    }

    private static JLabel labelWithText(Container root, String text) {
        return labels(root).stream().filter(label -> text.equals(label.getText())).findFirst().orElse(null);
    }

    private static JLabel labelWithIcon(Container root, Object icon) {
        return labels(root).stream().filter(label -> label.getIcon() == icon).findFirst().orElse(null);
    }

    private static Rectangle boundsInHeader(CollapsibleSection section, Component component) {
        return SwingUtilities.convertRectangle(component.getParent(), component.getBounds(), header(section));
    }

    private static void layOut(Container container) {
        container.doLayout();
        for (Component child : container.getComponents()) {
            if (child instanceof Container nested) layOut(nested);
        }
    }

    private static <T extends Component> List<T> all(Container root, Class<T> type) {
        List<T> found = new ArrayList<>();
        for (Component child : root.getComponents()) {
            if (type.isInstance(child)) found.add(type.cast(child));
            if (child instanceof Container nested) found.addAll(all(nested, type));
        }
        return found;
    }

    private static List<JLabel> labels(Container root) {
        List<JLabel> found = new ArrayList<>();
        for (Component child : root.getComponents()) {
            if (child instanceof JLabel label) found.add(label);
            if (child instanceof Container nested) found.addAll(labels(nested));
        }
        return found;
    }

    public void testTheTitleIsReadable() {
        CollapsibleSection section = new CollapsibleSection("Tags", new javax.swing.JPanel());

        assertEquals("Tags", section.title());
        assertEquals("Unknown table", new CollapsibleSection(null, new javax.swing.JPanel()).title());
    }

    private static final String LONG_TITLE = "test-dataform-489602.gold.gold_customer_purchase_summary";

    private static CollapsibleSection narrowSection(int width, JComponent... trailing) {
        CollapsibleSection section = new CollapsibleSection(LONG_TITLE, new JPanel());
        section.setSubtitle("incremental");
        section.setTrailing(trailing);
        section.setSize(width, 200);
        layOut(section);
        return section;
    }

    private static TrimmedTextField titleField(CollapsibleSection section) {
        return (TrimmedTextField) all(header(section), JTextComponent.class).getFirst();
    }

    public void testANarrowHeaderShrinksTheTitleAndSubtitleBeforeTheTrailingComponents() {
        JLabel cost = new JLabel("1.2 GB \u2248 \u20ac0.0100");
        CollapsibleSection section = narrowSection(320, cost);

        Rectangle title = boundsInHeader(section, titleField(section));
        Rectangle subtitle = boundsInHeader(section, labelWithText(header(section), "incremental"));
        Rectangle costBounds = boundsInHeader(section, cost);
        assertEquals(cost.getPreferredSize().width, costBounds.width);
        assertTrue("the cost must stay in the header", costBounds.x + costBounds.width <= header(section).getWidth());
        assertTrue("the title must end before the subtitle", title.x + title.width <= subtitle.x);
        assertTrue("the line's margins must stay between the subtitle and the cost",
                subtitle.x + subtitle.width + JBUI.scale(24) <= costBounds.x);
    }

    public void testAShrunkTitleShowsTheEndOfTheNameAndKeepsItsWholeText() {
        CollapsibleSection section = narrowSection(320, new JLabel("1.2 GB"));
        TrimmedTextField title = titleField(section);

        assertEquals(LONG_TITLE, title.getText());
        assertTrue(title.shownText(), title.shownText().startsWith("\u2026"));
        assertTrue(title.shownText(), title.shownText().endsWith("summary"));
        assertEquals(LONG_TITLE, title.getToolTipText(new MouseEvent(title, MouseEvent.MOUSE_MOVED, 0, 0, 1, 1, 0, false)));
    }

    public void testATitleThatFitsIsShownWholeWithoutATooltip() {
        CollapsibleSection section = narrowSection(1200, new JLabel("1.2 GB"));
        TrimmedTextField title = titleField(section);

        assertEquals(LONG_TITLE, title.shownText());
        assertNull(title.getToolTipText(new MouseEvent(title, MouseEvent.MOUSE_MOVED, 0, 0, 1, 1, 0, false)));
    }

    public void testTheSubtitleShrinksOnceTheTitleIsAtItsMinimum() {
        JLabel trailing = new JLabel("x".repeat(40));
        CollapsibleSection section = narrowSection(trailing.getPreferredSize().width + JBUI.scale(150), trailing);

        JLabel subtitle = labelWithText(header(section), "incremental");
        assertTrue(subtitle.getWidth() < subtitle.getPreferredSize().width);
        assertTrue(titleField(section).getWidth() > 0);
    }
}
