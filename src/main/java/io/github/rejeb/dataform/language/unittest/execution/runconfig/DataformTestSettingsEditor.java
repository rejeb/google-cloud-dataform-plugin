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
package io.github.rejeb.dataform.language.unittest.execution.runconfig;

import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import io.github.rejeb.dataform.language.unittest.execution.engine.DataformTestScope;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;
import javax.swing.JPanel;

public class DataformTestSettingsEditor extends SettingsEditor<DataformTestRunConfiguration> {

    private final ComboBox<DataformTestScope> scope = new ComboBox<>(DataformTestScope.values());
    private final JBTextField targetPath = new JBTextField();
    private final JPanel panel = FormBuilder.createFormBuilder()
            .addLabeledComponent("Scope:", scope)
            .addLabeledComponent("Project-relative path:", targetPath)
            .getPanel();

    public DataformTestSettingsEditor() {
        scope.addActionListener(e -> targetPath.setEnabled(scope.getItem() != DataformTestScope.ALL));
    }

    @Override
    protected void resetEditorFrom(@NotNull DataformTestRunConfiguration configuration) {
        scope.setItem(configuration.getScope());
        targetPath.setText(configuration.getTargetPath());
        targetPath.setEnabled(configuration.getScope() != DataformTestScope.ALL);
    }

    @Override
    protected void applyEditorTo(@NotNull DataformTestRunConfiguration configuration) {
        configuration.setScope(scope.getItem());
        configuration.setTargetPath(targetPath.getText().trim());
    }

    @Override
    protected @NotNull JComponent createEditor() {
        return panel;
    }
}
