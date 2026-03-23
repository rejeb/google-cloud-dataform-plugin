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

import com.intellij.execution.configurations.RunConfigurationOptions;
import com.intellij.openapi.components.StoredProperty;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.Mode;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class DataformWorkflowRunConfigurationOptions extends RunConfigurationOptions {

    private final StoredProperty<String> workspaceId =
            string("").provideDelegate(this, "workspaceId");
    private final StoredProperty<Mode> selectedMode =
            doEnum(Mode.ACTIONS, Mode.class).provideDelegate(this, "selectedMode");
    private final StoredProperty<Boolean> transitiveDependenciesIncluded =
            property(false).provideDelegate(this, "transitiveDependenciesIncluded");

    private final StoredProperty<Boolean> transitiveDependentsIncluded =
            property(false).provideDelegate(this, "transitiveDependentsIncluded");

    private final StoredProperty<Boolean> fullyRefreshIncrementalTables =
            property(false).provideDelegate(this, "fullyRefreshIncrementalTables");

    private final StoredProperty<List<String>> includedTags =
            this.<String>list().provideDelegate(this, "includedTags");

    private final StoredProperty<List<String>> includedTargets =
            this.<String>list().provideDelegate(this, "includedTargets");


    public String getWorkspaceId() {
        return workspaceId.getValue(this);
    }

    public void setWorkspaceId(String v) {
        workspaceId.setValue(this, v);
    }

    public Mode getSelectedMode() {
        return selectedMode.getValue(this);
    }

    public void setSelectedMode(Mode v) {
        selectedMode.setValue(this, v);
    }

    public @NotNull List<String> getIncludedTags() {
        List<String> v = includedTags.getValue(this);
        return v != null ? v : new ArrayList<>();
    }

    public void setIncludedTags(@NotNull List<String> v) {
        includedTags.setValue(this, new ArrayList<>(v));
    }

    public @NotNull List<String> getIncludedTargets() {
        List<String> v = includedTargets.getValue(this);
        return v != null ? v : new ArrayList<>();
    }

    public void setIncludedTargets(@NotNull List<String> v) {
        includedTargets.setValue(this, new ArrayList<>(v));
    }

    public boolean isTransitiveDependenciesIncluded() {
        return transitiveDependenciesIncluded.getValue(this);
    }

    public void setTransitiveDependenciesIncluded(boolean v) {
        transitiveDependenciesIncluded.setValue(this, v);
    }

    public boolean isTransitiveDependentsIncluded() {
        return transitiveDependentsIncluded.getValue(this);
    }

    public void setTransitiveDependentsIncluded(boolean v) {
        transitiveDependentsIncluded.setValue(this, v);
    }

    public boolean isFullyRefreshIncrementalTables() {
        return fullyRefreshIncrementalTables.getValue(this);
    }

    public void setFullyRefreshIncrementalTables(boolean v) {
        fullyRefreshIncrementalTables.setValue(this, v);
    }
}
