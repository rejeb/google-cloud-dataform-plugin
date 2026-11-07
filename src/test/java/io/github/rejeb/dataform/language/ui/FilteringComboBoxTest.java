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

import javax.swing.ComboBoxModel;
import javax.swing.text.JTextComponent;
import java.util.ArrayList;
import java.util.List;

public class FilteringComboBoxTest extends BasePlatformTestCase {

    private static final List<String> REGIONS =
            List.of("australia-southeast1", "europe-west1", "europe-west2", "us-central1", "us-west1");

    public void testTypingKeepsTheValuesContainingTheText() {
        FilteringComboBox combo = new FilteringComboBox(REGIONS);

        type(combo, "west");

        assertEquals(List.of("europe-west1", "europe-west2", "us-west1"), shown(combo));
        assertEquals("west", combo.getText());
    }

    public void testValuesStartingWithTheTextComeFirst() {
        FilteringComboBox combo = new FilteringComboBox(REGIONS);

        type(combo, "us");

        assertEquals(List.of("us-central1", "us-west1", "australia-southeast1"), shown(combo));
    }

    public void testMatchingIgnoresCase() {
        FilteringComboBox combo = new FilteringComboBox(REGIONS);

        type(combo, "CENTRAL");

        assertEquals(List.of("us-central1"), shown(combo));
    }

    public void testClearingTheTextShowsEveryValue() {
        FilteringComboBox combo = new FilteringComboBox(REGIONS);
        type(combo, "west");

        type(combo, "");

        assertEquals(REGIONS, shown(combo));
    }

    public void testPickingAMatchKeepsTheFilteredList() {
        FilteringComboBox combo = new FilteringComboBox(REGIONS);
        type(combo, "west");

        combo.setSelectedIndex(1);

        assertEquals("europe-west2", combo.getText());
        assertEquals(List.of("europe-west1", "europe-west2", "us-west1"), shown(combo));
    }

    public void testSettingTheTextShowsEveryValue() {
        FilteringComboBox combo = new FilteringComboBox(REGIONS);
        type(combo, "west");

        combo.setText("us-central1");

        assertEquals("us-central1", combo.getText());
        assertEquals(REGIONS, shown(combo));
    }

    public void testATextOutsideTheValuesIsKept() {
        FilteringComboBox combo = new FilteringComboBox(REGIONS);

        combo.setText("northamerica-south9");

        assertEquals("northamerica-south9", combo.getText());
        assertEquals("northamerica-south9", combo.getSelectedItem());
    }

    public void testNewValuesKeepTheTypedText() {
        FilteringComboBox combo = new FilteringComboBox(List.of());
        type(combo, "runner");

        combo.setItems(List.of("runner@p.iam.gserviceaccount.com", "admin@p.iam.gserviceaccount.com"));

        assertEquals("runner", combo.getText());
        assertEquals(List.of("runner@p.iam.gserviceaccount.com", "admin@p.iam.gserviceaccount.com"), shown(combo));
    }

    public void testTheWidthFollowsTheLongestValueNotTheMatches() {
        FilteringComboBox combo = new FilteringComboBox(REGIONS);

        type(combo, "us-c");

        assertEquals("australia-southeast1", combo.getPrototypeDisplayValue());
    }

    static void type(FilteringComboBox combo, String text) {
        ((JTextComponent) combo.getEditor().getEditorComponent()).setText(text);
    }

    static List<String> shown(FilteringComboBox combo) {
        ComboBoxModel<String> model = combo.getModel();
        List<String> values = new ArrayList<>();
        for (int i = 0; i < model.getSize(); i++) {
            values.add(model.getElementAt(i));
        }
        return values;
    }
}
