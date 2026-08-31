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
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
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
 * way into the Find window — so the window is built here. What it lists comes from the same
 * reference search Find Usages runs, and its footer hands the same column to that window when the
 * list outgrows a popup.</p>
 */
public final class ColumnUsagesPopup {

    private static final int MAX_HEIGHT = 600;
    private static final int MIN_WIDTH = 320;
    private static final int MAX_WIDTH = 1000;

    private ColumnUsagesPopup() {
    }

    /** Opens the window for a column. Does nothing when nothing is known about it. */
    public static void show(@NotNull Project project,
                            @NotNull Editor editor,
                            @NotNull ColumnWindowTarget target,
                            @Nullable PsiElement caretReference) {
        List<ColumnUsageRow> rows = ColumnUsageRows.of(project, target, caretReference);
        if (rows.isEmpty()) return;
        new Window(project, target, rows).show(editor);
    }

    /** Hands the column to the Find window, which holds a list a popup should not. */
    static void openInFindWindow(@NotNull Project project, @NotNull ColumnWindowTarget target) {
        if (target.searchTargets().isEmpty()) return;
        FindManager.getInstance(project).findUsages(target.searchTargets().getFirst());
    }

    static @NotNull String findWindowShortcutText() {
        return System.getProperty("os.name", "").toLowerCase().contains("mac")
                ? "⇧⌘F7" : "Shift+Ctrl+F7";
    }

    /** The popup itself: a list whose headings fold, over a footer that opens the Find window. */
    private static final class Window {

        private final Project project;
        private final ColumnWindowTarget target;
        private final List<ColumnUsageRow> all;
        private final Set<String> folded = new LinkedHashSet<>();
        private final DefaultListModel<ColumnUsageRow> model = new DefaultListModel<>();
        private final JBList<ColumnUsageRow> list = new JBList<>(model);
        private JBScrollPane scroller;
        private JBPopup popup;

        Window(Project project, ColumnWindowTarget target, List<ColumnUsageRow> all) {
            this.project = project;
            this.target = target;
            this.all = all;
        }

        void show(@NotNull Editor editor) {
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

            JPanel content = new JPanel(new BorderLayout());
            content.add(scroller, BorderLayout.CENTER);

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
                JLabel count = new JLabel("   " + row.count());
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
