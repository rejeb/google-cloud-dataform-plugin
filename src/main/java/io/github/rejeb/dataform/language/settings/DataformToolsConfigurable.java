/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
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

import com.intellij.openapi.options.Configurable;

import javax.swing.*;

public class DataformToolsConfigurable implements Configurable {

    private DataformToolsSettingsPanel panel;

    @Override
    public String getDisplayName() {
        return "Dataform Tools";
    }

    @Override
    public JComponent createComponent() {
        panel = new DataformToolsSettingsPanel();
        return panel.getPanel();
    }

    @Override
    public boolean isModified() {
        DataformToolsSettings service = DataformToolsSettings.getInstance();
        return !panel.getCliExecutablePath().equals(service.getCliExecutablePath())
                || !panel.getCoreInstallPath().equals(service.getCoreInstallPath());
    }

    @Override
    public void apply() {
        DataformToolsSettings.getInstance().update(
                panel.getCliExecutablePath(),
                panel.getCoreInstallPath()
        );
    }

    @Override
    public void reset() {
        DataformToolsSettings service = DataformToolsSettings.getInstance();
        panel.setCliExecutablePath(service.getCliExecutablePath());
        panel.setCoreInstallPath(service.getCoreInstallPath());
    }
}
