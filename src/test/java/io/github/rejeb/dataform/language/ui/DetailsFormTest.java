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
import com.intellij.ui.TitledSeparator;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.scale.JBUIScale;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Component;
import java.awt.Container;
import java.awt.GridBagLayout;
import java.util.ArrayList;
import java.util.List;

public class DetailsFormTest extends BasePlatformTestCase {

    public void testGroupsAndRows() {
        JBTextField status = DetailsForm.create().group("Execution").row("Status");
        DetailsForm form = DetailsForm.create().group("Execution");
        JBTextField value = form.row("Status");
        form.row("Duration", new JLabel("4 s"));

        JPanel panel = form.build();

        assertEquals(1, all(panel, TitledSeparator.class).size());
        assertEquals("Execution", all(panel, TitledSeparator.class).getFirst().getText());
        List<String> texts = all(panel, JLabel.class).stream().map(JLabel::getText).toList();
        assertTrue(texts.toString(), texts.contains("Status"));
        assertTrue(texts.toString(), texts.contains("Duration"));
        assertFalse(value.isEditable());
        assertEquals("—", value.getText());
        assertNotSame(status, value);
    }

    public void testKeysAreGrayed() {
        DetailsForm form = DetailsForm.create();
        form.row("Status");

        JLabel key = all(form.build(), JLabel.class).stream()
                .filter(label -> "Status".equals(label.getText())).findFirst().orElseThrow();

        assertEquals(com.intellij.util.ui.UIUtil.getContextHelpForeground(), key.getForeground());
    }

    public void testColumnsKeepTheirOrder() {
        JPanel first = new JPanel();
        JPanel second = new JPanel();

        JPanel columns = DetailsForm.columns(first, second);

        List<JComponent> children = new ArrayList<>();
        for (Component child : columns.getComponents()) children.add((JComponent) child);
        assertEquals(List.of(first, second), children);
    }

    private static <T> List<T> all(Container root, Class<T> type) {
        List<T> found = new ArrayList<>();
        for (Component child : root.getComponents()) {
            if (type.isInstance(child)) found.add(type.cast(child));
            if (child instanceof Container nested) found.addAll(all(nested, type));
        }
        return found;
    }

    public void testGroupGapsAreScaledOnce() {
        float userScale = JBUIScale.scale(1f);
        JBUIScale.setUserScaleFactor(2f);
        try {
            DetailsForm details = DetailsForm.create().group("Job");
            details.row("Job ID");
            JPanel form = (JPanel) details.group("Cost").build().getComponent(0);
            TitledSeparator separator = all(form, TitledSeparator.class).get(1);

            assertEquals(16, ((GridBagLayout) form.getLayout()).getConstraints(separator).insets.top);
        } finally {
            JBUIScale.setUserScaleFactor(userScale);
        }
    }
}
