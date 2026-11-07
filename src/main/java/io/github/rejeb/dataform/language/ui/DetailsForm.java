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

import com.intellij.ui.TitledSeparator;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.GridLayout;

/**
 * Builds the key/value forms of the detail views: groups under a titled separator, grayed keys
 * on the left and selectable values on the right.
 */
public final class DetailsForm {

    private static final String NO_VALUE = "—";

    private final FormBuilder builder = FormBuilder.createFormBuilder().setVerticalGap(2);

    private DetailsForm() {
    }

    /**
     * Starts an empty form.
     */
    public static @NotNull DetailsForm create() {
        return new DetailsForm();
    }

    /**
     * Starts a group of rows under a titled separator.
     */
    public @NotNull DetailsForm group(@NotNull String title) {
        builder.addComponent(new TitledSeparator(title), 8);
        return this;
    }

    /**
     * Adds a row showing the given component as its value.
     */
    public @NotNull DetailsForm row(@NotNull String key, @NotNull JComponent value) {
        builder.addLabeledComponent(keyLabel(key), value);
        return this;
    }

    /**
     * Adds a row whose value is a selectable text, initially {@code —}, and returns that text for
     * the caller to update.
     */
    public @NotNull JBTextField row(@NotNull String key) {
        JBTextField value = ReadOnlyTextFields.singleLine(NO_VALUE);
        row(key, value);
        return value;
    }

    /**
     * The form, aligned to the top of the space it is given.
     */
    public @NotNull JPanel build() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        JPanel form = builder.getPanel();
        form.setOpaque(false);
        panel.add(form, BorderLayout.NORTH);
        return panel;
    }

    /**
     * Lays forms out side by side in columns of equal width.
     */
    public static @NotNull JPanel columns(@NotNull JComponent... forms) {
        JPanel panel = new JPanel(new GridLayout(1, forms.length, JBUI.scale(24), 0));
        panel.setOpaque(false);
        for (JComponent form : forms) {
            panel.add(form);
        }
        return panel;
    }

    private static @NotNull JBLabel keyLabel(@NotNull String key) {
        JBLabel label = new JBLabel(key);
        label.setForeground(UIUtil.getContextHelpForeground());
        return label;
    }
}
