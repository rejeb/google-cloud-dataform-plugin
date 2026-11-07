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
package io.github.rejeb.dataform.language.settings;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.ui.TitledSeparator;
import com.intellij.ui.components.fields.ExpandableTextField;
import io.github.rejeb.dataform.language.ui.StatusLabel;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;

public class DataformToolsSettingsPanelTest extends BasePlatformTestCase {

    public void testThePageIsGroupedUnderTitledSeparators() {
        DataformToolsSettingsPanel panel = new DataformToolsSettingsPanel();

        List<String> groups = all(panel.getPanel(), TitledSeparator.class).stream().map(TitledSeparator::getText).toList();

        assertEquals(List.of("Dataform CLI", "SQLFluff formatter", "Editor", "BigQuery cost"), groups);
    }

    public void testExtraArgsAreAnExpandableField() {
        DataformToolsSettingsPanel panel = new DataformToolsSettingsPanel();

        panel.setSqlfluffExtraArgs("--dialect bigquery --templater raw");

        ExpandableTextField field = assertOneElement(all(panel.getPanel(), ExpandableTextField.class));
        assertEquals("--dialect bigquery --templater raw", field.getText());
        assertEquals("--dialect bigquery --templater raw", panel.getSqlfluffExtraArgs());
    }

    public void testTheInstallStatusIsAStatusLabel() {
        DataformToolsSettingsPanel panel = new DataformToolsSettingsPanel();

        List<StatusLabel> labels = all(panel.getPanel(), StatusLabel.class);

        assertTrue(labels.stream().anyMatch(label -> label.getText().contains("Node.js")));
    }

    static <T extends Component> List<T> all(Container root, Class<T> type) {
        List<T> found = new ArrayList<>();
        for (Component child : root.getComponents()) {
            if (type.isInstance(child)) {
                found.add(type.cast(child));
            }
            if (child instanceof Container container) {
                found.addAll(all(container, type));
            }
        }
        return found;
    }
}
