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
package io.github.rejeb.dataform.language.gcp.execution.workflow.runconfig;

import com.intellij.icons.AllIcons;
import com.intellij.ui.JBColor;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A compact field that shows selected items as comma-separated text
 * and opens a floating panel with a filterable checkbox list on click.
 * <p>
 * Integrates naturally with IntelliJ/FlatLaf themes.
 */
public class CheckboxMultiSelectField extends JPanel {

    // ── State ────────────────────────────────────────────────────────────────
    private final List<String> allItems = new ArrayList<>();
    private final Set<String> selectedItems = new LinkedHashSet<>();

    // ── Display ──────────────────────────────────────────────────────────────
    private final JTextField displayField;
    private JWindow floatingPanel;

    public CheckboxMultiSelectField() {
        super(new BorderLayout());

        displayField = new JTextField();
        displayField.setEditable(false);
        displayField.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));

        // Bouton flèche positionné à l'intérieur du champ via insets
        JButton arrowButton = new JButton(AllIcons.General.ChevronDown);
        arrowButton.setFocusable(false);
        arrowButton.setBorderPainted(false);
        arrowButton.setContentAreaFilled(false);
        arrowButton.setOpaque(false);
        arrowButton.setFont(arrowButton.getFont().deriveFont(10f));
        arrowButton.setMargin(JBUI.emptyInsets());
        arrowButton.setPreferredSize(new Dimension(20, 0));
        arrowButton.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        displayField.setColumns(1);
        // Padding droit dans le textfield pour que le texte ne passe pas sous le bouton
        displayField.setBorder(BorderFactory.createCompoundBorder(UIManager.getBorder("TextField.border"), JBUI.Borders.emptyRight(22)));

        // Superposition bouton sur le champ via JLayeredPane
        JLayeredPane layered = new JLayeredPane() {
            @Override
            public void doLayout() {
                displayField.setBounds(0, 0, getWidth(), getHeight());
                int btnW = 20;
                arrowButton.setBounds(getWidth() - btnW - 4, 0, btnW, getHeight());
            }

            @Override
            public Dimension getPreferredSize() {
                return displayField.getPreferredSize();
            }
        };
        layered.add(displayField, JLayeredPane.DEFAULT_LAYER);
        layered.add(arrowButton, JLayeredPane.PALETTE_LAYER);

        add(layered, BorderLayout.CENTER);

        MouseAdapter openOnClick = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                togglePopup();
            }
        };
        displayField.addMouseListener(openOnClick);
        arrowButton.addActionListener(e -> togglePopup());
    }


    // ── Public API ───────────────────────────────────────────────────────────

    public void setItems(@NotNull Collection<String> items) {
        allItems.clear();
        allItems.addAll(items);
    }

    public void setSelectedItems(@NotNull Collection<String> items) {
        selectedItems.clear();
        selectedItems.addAll(items);
        refreshDisplay();
    }

    @NotNull
    public List<String> getSelectedItems() {
        return new ArrayList<>(selectedItems);
    }

    // ── Popup ────────────────────────────────────────────────────────────────

    private void togglePopup() {
        if (floatingPanel != null && floatingPanel.isVisible()) {
            floatingPanel.dispose();
            floatingPanel = null;
            return;
        }
        showPopup();
    }

    private void showPopup() {
        Window owner = SwingUtilities.getWindowAncestor(this);
        floatingPanel = new JWindow(owner);
        floatingPanel.setType(Window.Type.POPUP);

        JPanel content = buildPopupContent();
        content.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(JBColor.border()), JBUI.Borders.empty(4)));

        floatingPanel.setContentPane(content);
        floatingPanel.pack();
        floatingPanel.setSize(Math.max(getWidth(), 240), 260);

        // Position below the field
        Point loc = getLocationOnScreen();
        floatingPanel.setLocation(loc.x, loc.y + getHeight());
        floatingPanel.setVisible(true);

        // Close on focus lost
        floatingPanel.addWindowFocusListener(new WindowAdapter() {
            @Override
            public void windowLostFocus(WindowEvent e) {
                floatingPanel.dispose();
                floatingPanel = null;
            }
        });

        // Close on Escape
        content.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close");
        content.getActionMap().put("close", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                floatingPanel.dispose();
                floatingPanel = null;
            }
        });
    }

    @NotNull
    private JPanel buildPopupContent() {
        // ── Search field ──────────────────────────────────────────────────
        SearchTextField searchField = new SearchTextField(false);
        searchField.getTextEditor().putClientProperty("JTextField.placeholderText", "Filter...");

        // ── Checkbox list model ───────────────────────────────────────────
        DefaultListModel<String> model = new DefaultListModel<>();
        allItems.forEach(model::addElement);

        JList<String> list = new JBList<>(model);
        list.setCellRenderer(new CheckboxListCellRenderer(selectedItems));
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setBackground(UIManager.getColor("List.background"));

        // ── Select all ────────────────────────────────────────────────────
        JBCheckBox selectAll = new JBCheckBox("Select all");
        updateSelectAll(selectAll, allItems);

        // ── Wire: list click → toggle selection ───────────────────────────
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int idx = list.locationToIndex(e.getPoint());
                if (idx < 0) return;
                String item = model.getElementAt(idx);
                if (selectedItems.contains(item)) selectedItems.remove(item);
                else selectedItems.add(item);
                list.repaint();
                String filter = searchField.getText().trim();
                updateSelectAll(selectAll, getFilteredItems(filter));
                refreshDisplay();
            }
        });

        // ── Wire: search → filter model ───────────────────────────────────
        searchField.addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                filter();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                filter();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                filter();
            }

            private void filter() {
                String text = searchField.getText().trim().toLowerCase();
                model.clear();
                getFilteredItems(text).forEach(model::addElement);
                updateSelectAll(selectAll, getFilteredItems(text));
            }
        });

        // ── Wire: select all ──────────────────────────────────────────────
        selectAll.addActionListener(e -> {
            String filter = searchField.getText().trim().toLowerCase();
            List<String> visible = getFilteredItems(filter);
            if (selectAll.isSelected()) selectedItems.addAll(visible);
            else selectedItems.removeAll(visible);
            list.repaint();
            refreshDisplay();
        });

        // ── Layout ────────────────────────────────────────────────────────
        JPanel top = new JPanel(new BorderLayout(0, 2));
        top.add(searchField, BorderLayout.NORTH);
        top.add(selectAll, BorderLayout.SOUTH);

        JBScrollPane scroll = new JBScrollPane(list);
        scroll.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, JBColor.border()));

        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.add(top, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    @NotNull
    private List<String> getFilteredItems(@NotNull String filter) {
        if (filter.isEmpty()) return new ArrayList<>(allItems);
        String lc = filter.toLowerCase();
        return allItems.stream().filter(s -> s.toLowerCase().contains(lc)).collect(Collectors.toList());
    }

    private void updateSelectAll(@NotNull JBCheckBox selectAll, @NotNull List<String> visible) {
        if (visible.isEmpty()) {
            selectAll.setSelected(false);
            selectAll.setEnabled(false);
            return;
        }
        selectAll.setEnabled(true);
        long checked = visible.stream().filter(selectedItems::contains).count();
        selectAll.setSelected(checked == visible.size());
    }

    private void refreshDisplay() {
        displayField.setText(String.join(", ", selectedItems));
    }

    // ── Checkbox cell renderer ────────────────────────────────────────────────

    private static final class CheckboxListCellRenderer implements ListCellRenderer<String> {

        private final Set<String> selected;
        private final JBCheckBox checkBox = new JBCheckBox();

        CheckboxListCellRenderer(@NotNull Set<String> selected) {
            this.selected = selected;
            checkBox.setOpaque(true);
            checkBox.setBorder(JBUI.Borders.empty(2, 4));
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends String> list, String value, int index, boolean isSelected, boolean cellHasFocus) {
            checkBox.setText(value);
            checkBox.setSelected(selected.contains(value));

            Color bg = isSelected ? list.getSelectionBackground() : list.getBackground();
            checkBox.setBackground(bg);
            checkBox.setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());
            return checkBox;
        }
    }
}
