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
package io.github.rejeb.dataform.language.compilation;

import com.intellij.execution.BeforeRunTaskProvider;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.util.Key;
import io.github.rejeb.dataform.language.DataformIcons;
import io.github.rejeb.dataform.language.gcp.execution.workflow.runconfig.DataformWorkflowRunConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.concurrent.Future;

public final class DataformBuildTaskProvider extends BeforeRunTaskProvider<DataformBuildTask> {

    public static final Key<DataformBuildTask> ID = Key.create("Dataform.BuildTask");

    @Override
    public Key<DataformBuildTask> getId() { return ID; }

    @Override
    public String getName() { return "Dataform Compile"; }

    @Override
    public String getDescription(DataformBuildTask task) {
        return "Run dataform compile before launch";
    }

    @Override
    public @Nullable Icon getIcon() { return DataformIcons.FILE; }

    @Override
    public boolean isSingleton() { return true; }

    @Override
    public @Nullable DataformBuildTask createTask(@NotNull RunConfiguration runConfiguration) {
        // Comme RsBuildTaskProvider : ne créer une tâche que pour les run configs Dataform
        return runConfiguration instanceof DataformWorkflowRunConfiguration
                ? new DataformBuildTask(ID)
                : null;
    }

    @Override
    public boolean executeTask(@NotNull DataContext context,
                               @NotNull RunConfiguration configuration,
                               @NotNull ExecutionEnvironment environment,
                               @NotNull DataformBuildTask task) {
        if (!(configuration instanceof DataformWorkflowRunConfiguration)) return false;
        if (environment.getProject().isDisposed()) return false;

        try {
            Future<DataformBuildResult> future = DataformBuildManager.build(environment.getProject());
            DataformBuildResult result = future.get(); // bloque jusqu'à la fin
            return result.succeeded;
        } catch (Exception e) {
            return false;
        }
    }
}