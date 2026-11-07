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
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.dualView.TreeTableView;
import com.intellij.ui.treeStructure.treetable.TreeTableTree;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import io.github.rejeb.dataform.language.DataformIcons;
import io.github.rejeb.dataform.language.schema.sql.model.ColumnInfo;
import io.github.rejeb.dataform.language.ui.RoundedBox;

import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class TableSchemaSectionTest extends BasePlatformTestCase {

    public void testTheSubtitleCountsTheColumns() {
        assertTrue(labels(section(List.of(column("id"), column("name")))).contains("2 columns"));
        assertTrue(labels(section(List.of(column("id")))).contains("1 column"));
    }

    public void testAnUnknownSchemaIsSaidInTheSubtitle() {
        assertTrue(labels(section(null)).contains("no schema"));
    }

    public void testTheHeaderShowsTheIconOfTheActionType() {
        TableSchemaSection section = new TableSchemaSection("p.d.raw_orders", "declaration", null, null, () -> {
        });

        assertTrue(icons(section.getComponent(0)).contains(ActionTypeIcons.of("declaration")));
    }

    public void testAnEmptySchemaExplainsWhyAndAsksToRefresh() {
        TableSchemaSection section = section(null);

        TreeTableView view = section.view();
        assertEquals("No schema yet", view.getEmptyText().getText());
        assertEquals("The table does not exist in BigQuery yet. Refresh schema",
                view.getEmptyText().getSecondaryComponent().getCharSequence(false).toString());
    }

    public void testAnAssertionSaysThePluginReadsNoSchemaForIt() {
        TableSchemaSection section = new TableSchemaSection("assert_orders", "assertion", null, null, () -> {
        });

        TreeTableView view = section.view();
        assertEquals("No schema", view.getEmptyText().getText());
        assertEquals("The plugin does not read the schema of assertions.",
                view.getEmptyText().getSecondaryComponent().getCharSequence(false).toString());
    }

    public void testTheReasonFollowsTheActionAndItsDryRun() {
        assertEquals("Refresh the schema to load it from BigQuery.",
                TableSchemaSection.missingSchemaReason("declaration", null));
        assertEquals("The query has errors. Fix them to get its schema.",
                TableSchemaSection.missingSchemaReason("table", "Unrecognized name: x"));
        assertEquals("The table does not exist in BigQuery yet.",
                TableSchemaSection.missingSchemaReason("view", null));
    }

    public void testAnEmptyTableLeavesRoomForTheMessage() {
        assertEquals(JBUI.scale(110), section(null).view().getPreferredSize().height);
    }

    public void testTheToolbarCopiesTheColumnNamesAndRefreshesTheSchema() {
        AtomicInteger refreshed = new AtomicInteger();
        TableSchemaSection section = new TableSchemaSection("p.d.users", "table", List.of(column("id")), null,
                refreshed::incrementAndGet);

        List<AnAction> actions = toolbarActions(section);
        assertEquals(List.of(DataformIcons.COPY, DataformIcons.REFRESH),
                actions.stream().map(a -> a.getTemplatePresentation().getIcon()).toList());

        actions.get(1).actionPerformed(TestActionEvent.createTestEvent(actions.get(1)));
        assertEquals(1, refreshed.get());
    }

    public void testCopyIsHiddenWithoutColumns() {
        AnAction copy = toolbarActions(section(null)).getFirst();
        AnActionEvent event = TestActionEvent.createTestEvent(copy);

        copy.update(event);

        assertFalse(event.getPresentation().isVisible());
    }

    public void testTheTableSitsInARoundedBox() {
        TreeTableView view = section(List.of(column("id"))).view();

        assertNotNull(SwingUtilities.getAncestorOfClass(RoundedBox.class, view));
        assertNotNull(SwingUtilities.getAncestorOfClass(RoundedBox.class, view.getTableHeader()));
    }

    public void testTheHeaderRowIsThirtyPixelsHighInASmallerGrayedFont() {
        TreeTableView view = section(List.of(column("id"))).view();

        assertEquals(JBUI.scale(30), view.getTableHeader().getPreferredSize().height);
        Component cell = view.getTableHeader().getDefaultRenderer()
                .getTableCellRendererComponent(view, "Name", false, false, -1, 0);
        assertEquals(JBFont.medium().getSize(), cell.getFont().getSize());
        assertEquals(UIUtil.getContextHelpForeground(), cell.getForeground());
    }

    public void testTheRowsAreTwentySixPixelsHighOnTheEditorBackground() {
        TreeTableView view = section(List.of(column("id"))).view();

        assertEquals(JBUI.scale(26), view.getRowHeight());
        assertEquals(scheme().getDefaultBackground(), view.getBackground());
    }

    public void testTypesUseTheKeywordColorInTheEditorFont() {
        TreeTableView view = section(List.of(column("id"))).view();

        Component cell = cell(view, 0, 1);

        assertEquals(scheme().getAttributes(DefaultLanguageHighlighterColors.KEYWORD).getForegroundColor(),
                cell.getForeground());
        assertEquals(scheme().getFont(EditorFontType.PLAIN).getFamily(), cell.getFont().getFamily());
        assertEquals(JBFont.medium().getSize(), cell.getFont().getSize());
    }

    public void testModesAndDescriptionsAreGrayed() {
        TreeTableView view = section(List.of(new ColumnInfo("id", "STRING", "REQUIRED", "Identifier"))).view();

        assertEquals(UIUtil.getContextHelpForeground(), cell(view, 0, 2).getForeground());
        assertEquals(UIUtil.getContextHelpForeground(), cell(view, 0, 3).getForeground());
    }

    public void testRecordsAndColumnsHaveTheirOwnIcons() {
        ColumnInfo items = new ColumnInfo("items", "RECORD", "REPEATED", null, List.of(column("sku")));
        TreeTableView view = section(List.of(column("id"), items)).view();

        assertSame(DataformIcons.SCHEMA_COLUMN, nameIcon(view, 0));
        assertSame(DataformIcons.SCHEMA_RECORD, nameIcon(view, 1));
    }

    public void testTheCopyButtonFollowsTheName() {
        TableSchemaSection section = laidOut(List.of(column("id"), column("customer_lifetime_value")));
        ColoredTreeCellRenderer renderer = (ColoredTreeCellRenderer) section.view().getTree().getOriginalCellRenderer();

        Rectangle shortName = section.copyButtonBounds(0);
        Rectangle longName = section.copyButtonBounds(1);

        int textStart = renderer.getIpad().left + DataformIcons.SCHEMA_COLUMN.getIconWidth() + renderer.getIconTextGap();
        assertTrue(shortName.x > textStart);
        assertTrue(longName.x > shortName.x);
        assertEquals(DataformIcons.COPY.getIconWidth(), longName.width);
    }

    public void testTheRowMakesRoomForTheCopyButton() {
        TableSchemaSection section = laidOut(List.of(column("id")));
        Rectangle node = section.view().getTree().getRowBounds(0);
        Rectangle cell = section.view().getCellRect(0, 0, false);

        Rectangle button = section.copyButtonBounds(0);

        assertEquals(cell.x + node.x + node.width, button.x + button.width);
    }

    public void testClickingTheCopyButtonCopiesTheColumnName() throws Exception {
        TableSchemaSection section = laidOut(List.of(column("id"), column("name")));

        click(section.view(), center(section.copyButtonBounds(1)));

        assertEquals("name", clipboard());
    }

    public void testAFieldIsCopiedWithItsParentsUpToTheTopColumn() throws Exception {
        ColumnInfo geo = new ColumnInfo("geo", "RECORD", "NULLABLE", null, List.of(column("lat")));
        ColumnInfo address = new ColumnInfo("address", "RECORD", "REPEATED", null, List.of(geo));
        TableSchemaSection section = laidOut(List.of(address));
        section.view().getTree().expandRow(0);
        section.view().getTree().expandRow(1);

        click(section.view(), center(section.copyButtonBounds(2)));

        assertEquals("address.geo.lat", clipboard());
    }

    public void testTheCopyButtonMovesRightWithTheDepth() {
        ColumnInfo address = new ColumnInfo("addr", "RECORD", "NULLABLE", null, List.of(column("city")));
        TableSchemaSection section = laidOut(List.of(address));
        section.view().getTree().expandRow(0);

        assertTrue(section.copyButtonBounds(1).x > section.copyButtonBounds(0).x);
    }

    public void testClickingTheNameDoesNotCopy() throws Exception {
        TableSchemaSection section = laidOut(List.of(column("customer_id")));
        CopyPasteManager.getInstance().setContents(new StringSelection("before"));
        Rectangle button = section.copyButtonBounds(0);

        click(section.view(), new Point(button.x - JBUI.scale(20), button.y + button.height / 2));

        assertEquals("before", clipboard());
    }

    public void testTheCopyButtonSaysWhatItCopies() {
        ColumnInfo address = new ColumnInfo("address", "RECORD", "NULLABLE", null, List.of(column("city")));
        TableSchemaSection section = laidOut(List.of(address));
        section.view().getTree().expandRow(0);
        Point point = center(section.copyButtonBounds(1));

        assertEquals("Copy address.city", section.view().getToolTipText(mouse(section.view(), MouseEvent.MOUSE_MOVED, point)));
    }

    public void testTheCopyButtonShowsAHandCursor() {
        TableSchemaSection section = laidOut(List.of(column("id")));
        TreeTableView view = section.view();

        view.dispatchEvent(mouse(view, MouseEvent.MOUSE_MOVED, center(section.copyButtonBounds(0))));
        assertEquals(Cursor.HAND_CURSOR, view.getCursor().getType());

        Rectangle button = section.copyButtonBounds(0);
        view.dispatchEvent(mouse(view, MouseEvent.MOUSE_MOVED, new Point(button.x + button.width + JBUI.scale(30), button.y + 2)));
        assertEquals(Cursor.DEFAULT_CURSOR, view.getCursor().getType());
    }

    public void testTopLevelRecordsCanBeExpanded() {
        assertTrue(section(List.of(column("id"))).view().getTree().getShowsRootHandles());
    }

    public void testTheColumnsShareTheWidthLikeThePrototype() {
        TreeTableView view = section(List.of(column("id"))).view();
        int width = JBUI.scale(700);

        view.setSize(width, JBUI.scale(200));
        view.doLayout();

        int[] expected = SchemaColumnWidths.of(width);
        for (int i = 0; i < expected.length; i++) {
            assertEquals("column " + i, expected[i], view.getColumnModel().getColumn(i).getWidth());
        }
    }

    private static TableSchemaSection laidOut(List<ColumnInfo> columns) {
        TableSchemaSection section = section(columns);
        section.view().setSize(JBUI.scale(700), JBUI.scale(300));
        section.view().doLayout();
        return section;
    }

    private static Point center(Rectangle bounds) {
        return new Point(bounds.x + bounds.width / 2, bounds.y + bounds.height / 2);
    }

    private static void click(Component target, Point point) {
        for (int id : new int[]{MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED, MouseEvent.MOUSE_CLICKED}) {
            target.dispatchEvent(mouse(target, id, point));
        }
    }

    private static MouseEvent mouse(Component target, int id, Point point) {
        return new MouseEvent(target, id, System.currentTimeMillis(), 0, point.x, point.y, 1, false,
                id == MouseEvent.MOUSE_MOVED ? MouseEvent.NOBUTTON : MouseEvent.BUTTON1);
    }

    private static String clipboard() throws Exception {
        return (String) CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor);
    }

    private static TableSchemaSection section(List<ColumnInfo> columns) {
        return new TableSchemaSection("p.d.users", "table", columns, null, () -> {
        });
    }

    private static ColumnInfo column(String name) {
        return new ColumnInfo(name, "STRING", "NULLABLE", null);
    }

    private static EditorColorsScheme scheme() {
        return EditorColorsManager.getInstance().getGlobalScheme();
    }

    private static Component cell(TreeTableView view, int row, int column) {
        return view.getCellRenderer(row, column)
                .getTableCellRendererComponent(view, view.getValueAt(row, column), false, false, row, column);
    }

    private static Icon nameIcon(TreeTableView view, int row) {
        TreeTableTree tree = view.getTree();
        Object node = tree.getPathForRow(row).getLastPathComponent();
        ColoredTreeCellRenderer renderer = (ColoredTreeCellRenderer) tree.getOriginalCellRenderer();
        renderer.getTreeCellRendererComponent(tree, node, false, false, tree.getModel().isLeaf(node), row, false);
        return renderer.getIcon();
    }

    private static List<AnAction> toolbarActions(TableSchemaSection section) {
        ActionToolbar toolbar = all(section.getComponent(0), ActionToolbar.class).getFirst();
        return List.of(toolbar.getActionGroup().getChildren(null));
    }

    private static List<String> labels(TableSchemaSection section) {
        return all(section.getComponent(0), JLabel.class).stream().filter(Component::isVisible)
                .map(JLabel::getText).toList();
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
}
