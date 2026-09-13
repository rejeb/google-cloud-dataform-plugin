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
package io.github.rejeb.dataform.language.schema.sql.usages;

import com.intellij.find.FindManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.psi.PsiElement;
import com.intellij.ui.JBColor;
import io.github.rejeb.dataform.language.schema.sql.model.StructColumnPath;
import com.intellij.ui.components.ActionLink;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.BoxLayout;
import javax.swing.ScrollPaneConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The window a column opens: the columns it is built from, the places that read it, under headings
 * that fold.
 *
 * <p>The platform's declaration popup carries none of this — no headings, no title of our own, no
 * way into the Find window — so the window is built here. What it lists comes from the same search
 * Find Usages runs, and {@code Shift+Ctrl+F7} hands the same column to that window when the list
 * outgrows a popup.</p>
 *
 * <p>The window opens on the first reads the search finds, since a reader waits for the first rows
 * and not the last. A Usages group that stopped at that ceiling offers a link under the list, and
 * following it runs the search again for every read the project holds.</p>
 */
public final class ColumnUsagesPopup {

    private static final int MAX_HEIGHT = 600;
    private static final int MIN_WIDTH = 320;
    private static final int MAX_WIDTH = 1000;

    private ColumnUsagesPopup() {
    }

    /**
     * Opens the window for a column. Does nothing when nothing is known about it.
     *
     * <p>The rows are read off the event thread and the window opens once they arrive, so the
     * gesture returns straight away however much the search has to resolve.</p>
     */
    public static void show(@NotNull Project project,
                            @NotNull Editor editor,
                            @NotNull ColumnWindowTarget target,
                            @Nullable PsiElement caretReference) {
        ColumnUsageRowsLoader.load(project, editor, target, caretReference,
                ColumnUsageRows.MAX_READS, rows -> {
                    if (rows.isEmpty() || editor.isDisposed()) return;
                    new Window(project, editor, target, caretReference, rows).show();
                });
    }

    /**
     * Hands the column to the Find window, which holds a list a popup should not.
     *
     * <p>A field of a struct column is handed over as itself rather than as the element the caret
     * sits on. Find Usages starting from that element would search whatever it resolves to, which
     * for a field is an element of its column's type and not the field at all.</p>
     */
    static void openInFindWindow(@NotNull Project project, @NotNull ColumnWindowTarget target) {
        StructColumnPath path = target.structPath();
        if (path != null && path.isField()) {
            new StructFieldUsageTarget(project, path).findUsages();
            return;
        }
        if (target.searchTargets().isEmpty()) return;
        FindManager.getInstance(project).findUsages(target.searchTargets().getFirst());
    }

    /**
     * The popup itself: a list whose headings fold, a link loading the reads the search stopped
     * short of, and a shortcut into the Find window.
     */
    private static final class Window {

        private static final String LOAD_ALL = "Show all usages";
        private static final String LOADING = "Loading all usages\u2026";

        private final Project project;
        private final Editor editor;
        private final ColumnWindowTarget target;
        private final PsiElement caretReference;
        private final Set<String> folded = new LinkedHashSet<>();
        private final DefaultListModel<ColumnUsageRow> model = new DefaultListModel<>();
        private final JBList<ColumnUsageRow> list = new JBList<>(model);
        private final ActionLink loadAllLink =
                new ActionLink(LOAD_ALL, (ActionListener) e -> loadAll());
        private final JPanel footer = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        private List<ColumnUsageRow> all;
        private JBScrollPane scroller;
        private JBPopup popup;

        Window(Project project, Editor editor, ColumnWindowTarget target,
               @Nullable PsiElement caretReference, List<ColumnUsageRow> all) {
            this.project = project;
            this.editor = editor;
            this.target = target;
            this.caretReference = caretReference;
            this.all = all;
        }

        void show() {
            rebuild();
            list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            list.setCellRenderer(new RowRenderer(folded));
            list.setBorder(JBUI.Borders.empty(4, 0));
            selectFirstEntry();

            scroller = new JBScrollPane(list);
            scroller.setBorder(JBUI.Borders.empty());
            scroller.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            scroller.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
            fitToContent();

            footer.setBorder(JBUI.Borders.empty(4, 20, 6, 8));
            footer.add(loadAllLink);
            footer.setVisible(isTruncated());

            JPanel content = new JPanel(new BorderLayout());
            content.add(scroller, BorderLayout.CENTER);
            content.add(footer, BorderLayout.SOUTH);

            popup = JBPopupFactory.getInstance()
                    .createComponentPopupBuilder(content, list)
                    .setRequestFocus(true)
                    .setResizable(true)
                    .setMovable(false)
                    .createPopup();

            list.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent event) {
                    int index = list.locationToIndex(event.getPoint());
                    if (index >= 0) activate(model.getElementAt(index));
                }
            });
            list.registerKeyboardAction(e -> activate(list.getSelectedValue()),
                    KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), JComponent.WHEN_FOCUSED);
            list.registerKeyboardAction(e -> openFindWindow(),
                    KeyStroke.getKeyStroke(KeyEvent.VK_F7,
                            InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK),
                    JComponent.WHEN_FOCUSED);
            list.registerKeyboardAction(e -> openFindWindow(),
                    KeyStroke.getKeyStroke(KeyEvent.VK_F7,
                            InputEvent.SHIFT_DOWN_MASK | InputEvent.META_DOWN_MASK),
                    JComponent.WHEN_FOCUSED);

            popup.showInBestPositionFor(editor);
        }

        /** A heading folds, an entry opens. */
        private void activate(@Nullable ColumnUsageRow row) {
            if (row == null) return;
            if (row.isHeading()) {
                if (!folded.remove(row.heading())) folded.add(row.heading());
                rebuild();
                return;
            }
            OpenFileDescriptor target = row.target();
            if (popup != null) popup.cancel();
            if (target != null && target.canNavigate()) target.navigate(true);
        }

        private void openFindWindow() {
            if (popup != null) popup.cancel();
            openInFindWindow(project, target);
        }

        /** Whether the Usages group holds the reads the search stopped at rather than all of them. */
        private boolean isTruncated() {
            for (ColumnUsageRow row : all) {
                if (row.isHeading() && row.heading().equals(ColumnUsageRows.USAGES)) {
                    return row.isTruncated();
                }
            }
            return false;
        }

        /**
         * Runs the search again with no ceiling and replaces the rows once it is done. The link
         * says it is working meanwhile, and goes away once there is nothing left to load.
         */
        private void loadAll() {
            loadAllLink.setEnabled(false);
            loadAllLink.setText(LOADING);
            ColumnUsageRowsLoader.load(project, editor, target, caretReference,
                    ColumnUsageRows.UNBOUNDED, rows -> {
                        if (popup == null || popup.isDisposed()) return;
                        if (!rows.isEmpty()) all = rows;
                        footer.setVisible(isTruncated());
                        loadAllLink.setText(LOAD_ALL);
                        loadAllLink.setEnabled(true);
                        rebuild();
                        if (list.getSelectedIndex() < 0) selectFirstEntry();
                    });
        }

        /** Rebuilds the visible rows, leaving out the entries of a folded group. */
        private void rebuild() {
            ColumnUsageRow selected = list.getSelectedValue();
            model.clear();
            for (ColumnUsageRow row : all) {
                if (!row.isHeading() && folded.contains(row.group())) continue;
                model.addElement(row);
            }
            if (selected != null) {
                int index = model.indexOf(selected);
                if (index >= 0) list.setSelectedIndex(index);
            }
            fitToContent();
            if (popup != null && popup.isVisible()) popup.pack(true, true);
        }

        /**
         * Sizes the scroller to the rows it holds, so the window is only as tall as its content and
         * scrolls once that content passes {@value ColumnUsagesPopup#MAX_HEIGHT} pixels.
         */
        private void fitToContent() {
            if (scroller == null) return;
            scroller.setPreferredSize(null);
            list.invalidate();
            Dimension rows = list.getPreferredSize();
            int maxHeight = JBUI.scale(MAX_HEIGHT);
            int height = Math.min(rows.height, maxHeight);
            int width = rows.width;
            if (rows.height > maxHeight) {
                width += scroller.getVerticalScrollBar().getPreferredSize().width;
            }
            width = Math.clamp(width, JBUI.scale(MIN_WIDTH), JBUI.scale(MAX_WIDTH));
            scroller.setPreferredSize(new Dimension(width, height));
        }

        private void selectFirstEntry() {
            for (int i = 0; i < model.getSize(); i++) {
                if (!model.getElementAt(i).isHeading()) {
                    list.setSelectedIndex(i);
                    return;
                }
            }
        }

    }

    private static Color dimmed() {
        return new JBColor(new Color(0x6C707E), new Color(0x868A91));
    }


    /**
     * Draws a heading with the arrow its folded state calls for, and an entry as the expression it
     * reads with the column name picked out and the file pushed to the right.
     */
    static final class RowRenderer implements ListCellRenderer<ColumnUsageRow> {

        private final Set<String> folded;

        RowRenderer(Set<String> folded) {
            this.folded = folded;
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends ColumnUsageRow> list,
                                                      ColumnUsageRow row, int index,
                                                      boolean selected, boolean focused) {
            Color background = selected ? list.getSelectionBackground() : list.getBackground();
            Color foreground = selected ? list.getSelectionForeground() : list.getForeground();

            JPanel panel = new JPanel(new BorderLayout());
            panel.setBackground(background);
            panel.setBorder(JBUI.Borders.empty(2, row.isHeading() ? 6 : 20, 2, 8));

            if (row.isHeading()) {
                JPanel heading = new JPanel();
                heading.setLayout(new BoxLayout(heading, BoxLayout.X_AXIS));
                heading.setBackground(background);
                JLabel arrow = new JLabel(folded.contains(row.heading()) ? "›  " : "⌄  ");
                arrow.setForeground(selected ? foreground : dimmed());
                JLabel label = new JLabel(row.heading());
                label.setForeground(selected ? foreground : dimmed());
                label.setFont(label.getFont().deriveFont(Font.BOLD, label.getFont().getSize() - 1f));
                JLabel count = new JLabel("   " + row.count() + (row.isTruncated() ? "+" : ""));
                count.setForeground(selected ? foreground : dimmed());
                count.setFont(label.getFont().deriveFont(Font.PLAIN));
                heading.add(arrow);
                heading.add(label);
                heading.add(count);
                panel.add(heading, BorderLayout.WEST);
                return panel;
            }

            JPanel code = new JPanel();
            code.setLayout(new BoxLayout(code, BoxLayout.X_AXIS));
            code.setBackground(background);
            code.add(part(row.before(), monospace(), selected ? foreground : dimmed()));
            code.add(part(row.name(), monospace().deriveFont(Font.BOLD), foreground));
            code.add(part(row.after(), monospace(), selected ? foreground : dimmed()));
            panel.add(code, BorderLayout.WEST);

            JLabel location = new JLabel(row.location());
            location.setForeground(selected ? foreground : dimmed());
            location.setFont(location.getFont().deriveFont(location.getFont().getSize() - 1f));
            location.setBorder(JBUI.Borders.emptyLeft(24));
            panel.add(location, BorderLayout.EAST);
            return panel;
        }

        private JLabel part(String text, Font font, Color color) {
            JLabel label = new JLabel(text);
            label.setFont(font);
            label.setForeground(color);
            return label;
        }

        private Font monospace() {
            return EditorColorsManager.getInstance().getGlobalScheme().getFont(null);
        }
    }
}
