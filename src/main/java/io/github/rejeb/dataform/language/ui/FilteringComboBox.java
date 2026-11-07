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

import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.DocumentAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.AbstractListModel;
import javax.swing.ComboBoxEditor;
import javax.swing.ComboBoxModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * An editable combo box of strings whose list keeps, while the user types, the values containing the
 * typed text, ignoring case, those starting with it first. The text may be a value outside the list.
 *
 * <p>The selected item follows the typed text: the combo box writes its selected item into the
 * editor whenever its list changes or its popup opens, which would otherwise replace what the user
 * typed.
 */
public class FilteringComboBox extends ComboBox<String> {

    private final FilterModel model = new FilterModel();
    private final DocumentListener filter = new DocumentAdapter() {
        @Override
        protected void textChanged(@NotNull DocumentEvent e) {
            if (!configuringEditor) {
                filter();
            }
        }
    };
    private List<String> values = List.of();
    private boolean configuringEditor;
    private @Nullable JTextComponent editorText;

    /**
     * An editable combo box offering {@code values}.
     */
    public FilteringComboBox(@NotNull List<String> values) {
        setModel(model);
        setEditable(true);
        listenToEditor();
        addPropertyChangeListener("editor", e -> listenToEditor());
        setItems(values);
    }

    /**
     * Replaces the values; the list shows all of them and the text is kept.
     */
    public void setItems(@NotNull List<String> values) {
        this.values = List.copyOf(values);
        setPrototypeDisplayValue(this.values.stream().max(Comparator.comparingInt(String::length)).orElse(null));
        model.show(this.values, rawText());
    }

    /**
     * The text of the editor, trimmed, never null.
     */
    public @NotNull String getText() {
        return rawText().trim();
    }

    /**
     * Shows {@code text} in the editor and every value in the list.
     */
    public void setText(@NotNull String text) {
        model.show(values, text);
        configureEditor(getEditor(), text);
    }

    @Override
    public void configureEditor(ComboBoxEditor editor, Object item) {
        boolean outer = configuringEditor;
        configuringEditor = true;
        try {
            super.configureEditor(editor, item);
        } finally {
            configuringEditor = outer;
        }
    }

    /**
     * The values containing {@code text}, ignoring case, those starting with it first; every value
     * when the text is blank.
     */
    static @NotNull List<String> matching(@NotNull List<String> values, @NotNull String text) {
        String query = text.trim();
        if (query.isEmpty()) {
            return values;
        }
        List<String> starting = new ArrayList<>();
        List<String> containing = new ArrayList<>();
        for (String value : values) {
            if (StringUtil.startsWithIgnoreCase(value, query)) {
                starting.add(value);
            } else if (StringUtil.containsIgnoreCase(value, query)) {
                containing.add(value);
            }
        }
        starting.addAll(containing);
        return starting;
    }

    private void filter() {
        String text = rawText();
        model.show(matching(values, text), text);
        if (!isShowing() || editorText == null || !editorText.isFocusOwner()) {
            return;
        }
        if (isPopupVisible()) {
            hidePopup();
        }
        if (model.getSize() > 0) {
            showPopup();
        }
    }

    private @NotNull String rawText() {
        if (editorText != null) {
            return editorText.getText();
        }
        Object item = getEditor() == null ? null : getEditor().getItem();
        return item == null ? "" : item.toString();
    }

    private void listenToEditor() {
        if (editorText != null) {
            editorText.getDocument().removeDocumentListener(filter);
        }
        editorText = getEditor() != null && getEditor().getEditorComponent() instanceof JTextComponent text ? text : null;
        if (editorText != null) {
            editorText.getDocument().addDocumentListener(filter);
        }
    }

    private static final class FilterModel extends AbstractListModel<String> implements ComboBoxModel<String> {

        private List<String> shown = List.of();
        private @Nullable Object selected;

        void show(@NotNull List<String> values, @Nullable Object selection) {
            shown = values;
            selected = selection;
            fireContentsChanged(this, -1, -1);
        }

        @Override
        public int getSize() {
            return shown.size();
        }

        @Override
        public String getElementAt(int index) {
            return shown.get(index);
        }

        @Override
        public void setSelectedItem(@Nullable Object item) {
            if (!Objects.equals(selected, item)) {
                selected = item;
                fireContentsChanged(this, -1, -1);
            }
        }

        @Override
        public @Nullable Object getSelectedItem() {
            return selected;
        }
    }
}
