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

import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.ui.InlineBanner;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.util.ui.UIUtil;
import io.github.rejeb.dataform.language.DataformIcons;
import io.github.rejeb.dataform.language.ui.CodeBlock;
import io.github.rejeb.dataform.language.ui.sqlviewer.SqlViewer;
import io.github.rejeb.dataform.language.ui.sqlviewer.SqlViewerDrag;
import io.github.rejeb.dataform.language.ui.sqlviewer.SqlViewerHeights;

import javax.swing.Icon;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.datatransfer.DataFlavor;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class TableQuerySectionTest extends BasePlatformTestCase {

    private final List<TableQuerySection> created = new ArrayList<>();

    @Override
    protected void tearDown() throws Exception {
        try {
            created.forEach(TableQuerySection::dispose);
        } finally {
            super.tearDown();
        }
    }

    public void testOnlyTheQueryShowsNoTabs() {
        TableQuerySection section = section(query("SELECT 1", null, 0, null, 0));

        assertNull(section.tabs());
        assertTrue(section.queryViewer().hasText());
    }

    public void testOperationsBecomeCountedTabs() {
        TableQuerySection section = section(query("SELECT 1", "SET a = 1;\nSET b = 2;", 2, "DROP x;", 1));

        JBTabbedPane tabs = section.tabs();
        assertNotNull(tabs);
        List<String> titles = new ArrayList<>();
        for (int i = 0; i < tabs.getTabCount(); i++) titles.add(tabs.getTitleAt(i));
        assertEquals(List.of("Query", "Pre-operations (2)", "Post-operations (1)"), titles);
        assertEquals(0, tabs.getSelectedIndex());
    }

    public void testAutoAssertionsBecomeACountedTabAfterTheOperations() {
        TableQuerySection section = section(withAssertions(query("SELECT 1", "SET a = 1;", 1, null, 0)));

        JBTabbedPane tabs = section.tabs();
        assertNotNull(tabs);
        List<String> titles = new ArrayList<>();
        for (int i = 0; i < tabs.getTabCount(); i++) titles.add(tabs.getTitleAt(i));
        assertEquals(List.of("Query", "Pre-operations (1)", "Assertions (2)"), titles);
        assertEquals(0, tabs.getSelectedIndex());
    }

    public void testAutoAssertionsAloneStillMakeTabs() {
        TableQuerySection section = section(withAssertions(query("SELECT 1", null, 0, null, 0)));

        assertNotNull(section.tabs());
        assertEquals("Assertions (2)", section.tabs().getTitleAt(1));
    }

    public void testEachAssertionKeepsItsOwnBlockNamedByItsKind() {
        TableQuerySection section = section(withAssertions(query("SELECT 1", null, 0, null, 0)));

        List<TableQuerySection> assertions = section.assertionSections();

        assertEquals(List.of("uniqueKey_0", "rowConditions"), assertions.stream().map(TableQuerySection::title).toList());
        assertEquals(List.of(UNIQUE_KEY, ROW_CONDITIONS),
                assertions.stream().map(a -> a.getQuery().tableName()).toList());
        assertTrue(icons(assertions.getFirst().getComponent(0)).contains(DataformIcons.ACTION_ASSERTION));
        assertFalse(labels(assertions.getFirst().getComponent(0)).contains("assertion"));
        assertTrue(assertions.getFirst().queryViewer().hasText());
    }

    public void testAnAssertionErrorShowsInItsBlockAndMarksTheTab() {
        TableQuerySection section = section(withAssertions(query("SELECT 1", null, 0, null, 0)));

        section.showErrors(Map.of(UNIQUE_KEY, "Not found: Table p.ds.users"));

        assertTrue(section.assertionSections().getFirst().errorBanner().isVisible());
        assertFalse(section.errorBanner().isVisible());
        assertSame(DataformIcons.STATUS_FAILED, section.tabs().getIconAt(1));
        section.showErrors(Map.of());
        assertNull(section.tabs().getIconAt(1));
    }

    public void testDisposingTheSectionReleasesTheAssertionEditors() {
        TableQuerySection section = new TableQuerySection(withAssertions(query("SELECT 1", null, 0, null, 0)),
                PlainTextFileType.INSTANCE, getProject());
        TableQuerySection assertion = section.assertionSections().getFirst();

        section.dispose();

        assertNull(assertion.queryViewer().editor());
    }

    public void testTheHeaderShowsTheIconOfTheActionType() {
        TableQuerySection table = section(query("SELECT 1", null, 0, null, 0));
        TableQuerySection assertion = section(new FormattedCompiledQuery("p.ds.users_assertions_rowConditions",
                null, null, "SELECT 1", null, null, "assertion", 0, 0, 0));

        assertTrue(icons(table.getComponent(0)).contains(DataformIcons.ACTION_TABLE));
        assertTrue(icons(assertion.getComponent(0)).contains(DataformIcons.ACTION_ASSERTION));
    }

    public void testTheToolbarUsesThePrototypeIcons() {
        TableQuerySection section = section(query("SELECT 1", null, 0, null, 0));

        List<Icon> icons = toolbarActions(section).stream()
                .map(action -> action.getTemplatePresentation().getIcon())
                .toList();

        assertEquals(List.of(DataformIcons.COPY, DataformIcons.RUN, DataformIcons.EXTERNAL_LINK), icons);
    }

    public void testTheQueryIsShownInARoundedCodeBlock() {
        TableQuerySection section = section(query("SELECT 1", null, 0, null, 0));

        Component editor = section.queryViewer().editor().getComponent();

        assertNotNull(SwingUtilities.getAncestorOfClass(CodeBlock.class, editor));
    }

    public void testTheCodeBlockCornersInATabShowWhatIsBehindTheTabs() {
        TableQuerySection section = section(query("SELECT 1", "SET a = 1;", 1, null, 0));
        JBTabbedPane tabs = section.tabs();
        assertNotNull(tabs);
        tabs.setSize(400, tabs.getPreferredSize().height);
        layout(tabs);
        Component block = SwingUtilities.getAncestorOfClass(CodeBlock.class,
                section.queryViewer().editor().getComponent());
        Point corner = SwingUtilities.convertPoint(block, 0, 0, tabs);

        BufferedImage image = new BufferedImage(tabs.getWidth(), tabs.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            tabs.paint(g);
        } finally {
            g.dispose();
        }

        assertEquals(0, image.getRGB(corner.x, corner.y) >>> 24);
        assertEquals(0, image.getRGB(corner.x - 1, corner.y + 20) >>> 24);
    }

    public void testALongLineDoesNotWidenTheSection() {
        TableQuerySection shortLine = section(query("SELECT 1", null, 0, null, 0));
        TableQuerySection longLine = section(query(LONG_LINE, null, 0, null, 0));

        assertEquals(shortLine.getPreferredSize().width, longLine.getPreferredSize().width);
    }

    public void testALongLineInATabDoesNotWidenTheSection() {
        TableQuerySection shortLine = section(query("SELECT 1", "SET a = 1;", 1, null, 0));
        TableQuerySection longLine = section(query(LONG_LINE, "SET a = 1;", 1, null, 0));

        assertEquals(shortLine.getPreferredSize().width, longLine.getPreferredSize().width);
    }

    public void testAShortQueryDoesNotScroll() {
        TableQuerySection section = section(query("SELECT\n  1\nFROM x", null, 0, null, 0));

        assertEquals(0, section.queryViewer().editor().getSettings().getAdditionalLinesCount());
        assertEquals(0, section.queryViewer().editor().getSettings().getAdditionalColumnsCount());
    }

    public void testTheDryRunErrorHasATitleAndACopyLink() throws Exception {
        TableQuerySection section = section(query("SELECT 1", null, 0, null, 0));

        section.showError("Not found: Table p.ds.users");

        InlineBanner banner = section.errorBanner();
        assertTrue(messageOf(banner).contains("BigQuery dry run failed"));
        assertTrue(messageOf(banner).contains("Not found: Table p.ds.users"));
        LinkLabel<?> copy = links(banner).stream().filter(link -> "Copy error".equals(link.getText()))
                .findFirst().orElseThrow();
        copy.doClick();
        assertEquals("Not found: Table p.ds.users", CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor));
    }

    public void testANotCompiledActionSaysSoInItsHeader() {
        TableQuerySection section = section(new FormattedCompiledQuery("p.ds.users", null, null, null, null,
                "ReferenceError: undefinedFn is not defined"));

        assertTrue(messageOf(section.compilationBanner()).contains("This action did not compile"));
        assertEquals("Not compiled", section.costLabel().getText());
        assertEquals(UIUtil.getErrorForeground(), section.costLabel().getForeground());
    }

    public void testTheDryRunErrorShowsInsideTheSection() {
        TableQuerySection section = section(query("SELECT 1", null, 0, null, 0));

        section.showError("Not found: Table p.ds.users");

        InlineBanner banner = section.errorBanner();
        assertTrue(banner.isVisible());
        assertTrue(banner.getToolTipText().contains("Not found: Table p.ds.users"));
        section.showError(null);
        assertFalse(banner.isVisible());
    }

    public void testTheSubtitleIsTheActionType() {
        TableQuerySection section = section(query("SELECT 1", null, 0, null, 0));

        assertTrue(labels(section.getComponent(0)).contains("table"));
    }

    public void testACompilationErrorShowsAsABanner() {
        TableQuerySection section = section(new FormattedCompiledQuery("p.ds.users", null, null, null, null,
                "ReferenceError: undefinedFn is not defined\n    at definitions/users.sqlx:3:5"));

        InlineBanner banner = section.compilationBanner();
        assertNotNull(banner);
        assertTrue(banner.getToolTipText().contains("ReferenceError: undefinedFn is not defined"));
    }

    public void testTheEditorIsAsTallAsItsLinesUpToTwenty() {
        TableQuerySection shortOne = section(query("SELECT\n  1\nFROM x", null, 0, null, 0));
        TableQuerySection longOne = section(query("SELECT 1\n".repeat(60), null, 0, null, 0));

        int lineHeight = shortOne.queryViewer().editor().getLineHeight();
        int shortHeight = shortOne.queryViewer().editorAreaHeight();
        int longHeight = longOne.queryViewer().editorAreaHeight();

        assertTrue(shortHeight >= 3 * lineHeight && shortHeight < 4 * lineHeight);
        assertTrue(longHeight >= 20 * lineHeight && longHeight < 21 * lineHeight);
    }

    public void testAResizedBlockComesBackAtItsHeightWhenTheSectionIsRebuilt() {
        SqlViewerHeights heights = new SqlViewerHeights();
        FormattedCompiledQuery query = query("SELECT 1\n".repeat(60), "SET a = 1;", 1, null, 0);
        TableQuerySection first = section(query, heights);
        SqlViewer viewer = first.queryViewer();
        int automatic = viewer.editorAreaHeight();
        SqlViewerDrag.drag(viewer, 5 * viewer.editor().getLineHeight());

        TableQuerySection rebuilt = section(query, heights);
        TableQuerySection other = section(query, new SqlViewerHeights());

        assertEquals(viewer.editorAreaHeight(), rebuilt.queryViewer().editorAreaHeight());
        assertTrue(rebuilt.queryViewer().editorAreaHeight() > automatic);
        assertEquals(automatic, other.queryViewer().editorAreaHeight());
    }

    public void testResizingTheQueryLeavesTheOtherTabsOfTheActionAlone() {
        SqlViewerHeights heights = new SqlViewerHeights();
        String preOpsSql = "SET a = 1;\n".repeat(60);
        FormattedCompiledQuery query = query("SELECT 1\n".repeat(60), preOpsSql, 60, null, 0);
        TableQuerySection section = section(query, heights);
        SqlViewer preOps = viewerShowing(section, preOpsSql);
        int automatic = preOps.editorAreaHeight();

        SqlViewerDrag.drag(section.queryViewer(), 5 * preOps.editor().getLineHeight());

        assertEquals(automatic, viewerShowing(section(query, heights), preOpsSql).editorAreaHeight());
    }

    public void testTheCompilationErrorsShowAsPlainTextWithoutLineNumbers() {
        String errors = "ReferenceError: undefinedFn is not defined";
        TableQuerySection section = section(new FormattedCompiledQuery("p.ds.users", null, null, null, null, errors));

        SqlViewer viewer = viewerShowing(section, errors);

        assertFalse(viewer.editor().getSettings().isLineNumbersShown());
    }

    public void testCopySqlCopiesTheQuery() throws Exception {
        TableQuerySection section = section(query("SELECT 42", null, 0, null, 0));

        section.copySql();

        assertEquals("SELECT 42", CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor));
    }

    public void testRunHandsTheActionToThePreview() {
        AtomicReference<FormattedCompiledQuery> ran = new AtomicReference<>();
        FormattedCompiledQuery query = query("SELECT 1", null, 0, null, 0);
        TableQuerySection section = new TableQuerySection(query, PlainTextFileType.INSTANCE, getProject(), ran::set, null);
        created.add(section);

        section.runQuery();

        assertSame(query, ran.get());
    }

    private TableQuerySection section(FormattedCompiledQuery query) {
        TableQuerySection section = new TableQuerySection(query, PlainTextFileType.INSTANCE, getProject());
        created.add(section);
        return section;
    }

    private TableQuerySection section(FormattedCompiledQuery query, SqlViewerHeights heights) {
        TableQuerySection section = new TableQuerySection(query, PlainTextFileType.INSTANCE, getProject(),
                ignored -> {
                }, null, heights);
        created.add(section);
        return section;
    }

    private static SqlViewer viewerShowing(TableQuerySection section, String text) {
        return all(section, SqlViewer.class).stream()
                .filter(viewer -> text.equals(viewer.text()))
                .findFirst()
                .orElseThrow();
    }

    private static final String LONG_LINE = "SELECT " + "customer_id, ".repeat(200) + "1";
    private static final String UNIQUE_KEY ="p.ds_assertions.ds_users_assertions_uniqueKey_0";
    private static final String ROW_CONDITIONS = "p.ds_assertions.ds_users_assertions_rowConditions";

    private static FormattedCompiledQuery withAssertions(FormattedCompiledQuery table) {
        return table.withAssertions(List.of(
                new FormattedCompiledQuery(UNIQUE_KEY, null, null, "SELECT id FROM t GROUP BY id HAVING COUNT(*) > 1",
                        null, null, "assertion", 0, 0, 0),
                new FormattedCompiledQuery(ROW_CONDITIONS, null, null, "SELECT * FROM t WHERE NOT (id IS NOT NULL)",
                        null, null, "assertion", 0, 0, 0)));
    }

    private static FormattedCompiledQuery query(String sql, String preOps, int preOpsCount, String postOps, int postOpsCount) {
        return new FormattedCompiledQuery("p.ds.users", preOps, null, sql, postOps, null,
                "table", preOpsCount, 0, postOpsCount);
    }

    private static List<AnAction> toolbarActions(TableQuerySection section) {
        ActionToolbar toolbar = all(section.getComponent(0), ActionToolbar.class).getFirst();
        return List.of(toolbar.getActionGroup().getChildren(null));
    }

    private static String messageOf(InlineBanner banner) {
        return all(banner, JEditorPane.class).getFirst().getText();
    }

    private static List<LinkLabel> links(InlineBanner banner) {
        return all(banner, LinkLabel.class).stream().filter(Component::isVisible).toList();
    }

    private static List<Icon> icons(Component root) {
        return all(root, JLabel.class).stream().filter(Component::isVisible).map(JLabel::getIcon).toList();
    }

    private static <T> List<T> all(Component root, Class<T> type) {
        List<T> found = new ArrayList<>();
        if (type.isInstance(root)) found.add(type.cast(root));
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) found.addAll(all(child, type));
        }
        return found;
    }

    private static void layout(Component component) {
        if (component instanceof Container container) {
            container.doLayout();
            for (Component child : container.getComponents()) layout(child);
        }
    }

    private static List<String> labels(Component root) {
        List<String> found = new ArrayList<>();
        if (root instanceof JLabel label && label.isVisible()) found.add(label.getText());
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) found.addAll(labels(child));
        }
        return found;
    }
}
