/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
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
package io.github.rejeb.dataform.setup;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import com.intellij.openapi.ui.Messages;
import io.github.rejeb.dataform.language.util.NodeJsNpmUtils;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class DataformInstaller implements ProjectActivity {
    private static final Logger LOG = Logger.getInstance(DataformInstaller.class);
    private Boolean notifyUserNode = true;
    private Boolean notifyUserDataformCore = true;
    private Boolean notifyUserDataformCli = true;

    @Override
    public @Nullable Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
        ApplicationManager.getApplication().invokeLaterOnWriteThread(() -> {
            NodeInterpreterManager nodeInterpreterManager = NodeInterpreterManager.getInstance(project);

            if (nodeInterpreterManager.npmExecutable() == null && notifyUserNode) {
                notifyUserNode = false;
                NodeJsNpmUtils.showNpmConfigurationDialog(project);
                nodeInterpreterManager = NodeInterpreterManager.getInstance(project);
            }

            DataformInterpreterManager dataformInterpreterManager = DataformInterpreterManager.getInstance(project);
            if (dataformInterpreterManager.dataformCorePath().isEmpty() && notifyUserDataformCore) {
                notifyUserDataformCore = false;
                installDataformCore(project, nodeInterpreterManager);
            }

            if (dataformInterpreterManager.dataformCliDir().isEmpty() && notifyUserDataformCli) {
                notifyUserDataformCli = false;
                installDataformCli(project, nodeInterpreterManager);
            }
            DataformInterpreterManager.getInstance(project);
        });
        return DataformInterpreterManager.getInstance(project).dataformCorePath().isPresent();
    }

    private void installDataformCli(Project project, NodeInterpreterManager nodeInterpreterManager) {
        LOG.debug("Installing Dataform CLI...");
        int result = Messages.showOkCancelDialog(project,
                "Dataform Cli is not installed.\n\nWould you like to intall it ?",
                "Dataform Cli Not Available",
                "Install",
                "Cancel",
                Messages.getWarningIcon());

        if (result == Messages.OK) {
            NodeJsNpmUtils.installNodeJsLib("@dataform/cli",
                    project,
                    nodeInterpreterManager.npmExecutable().toFile(),
                    nodeInterpreterManager.nodeBinDir().toFile(),
                    nodeInterpreterManager.nodeInstallDir().toFile());
        }
    }

    private void installDataformCore(Project project, NodeInterpreterManager nodeInterpreterManager) {
        LOG.debug("Installing Dataform CORE...");

        int result = Messages.showOkCancelDialog(project,
                "Dataform Core is not installed.\n\nWould you like to intall it ?",
                "Dataform Core Not Available",
                "Install",
                "Cancel",
                Messages.getWarningIcon());

        if (result == Messages.OK) {
            NodeJsNpmUtils.installNodeJsLib("@dataform/core",
                    project,
                    nodeInterpreterManager.npmExecutable().toFile(),
                    nodeInterpreterManager.nodeBinDir().toFile(),
                    nodeInterpreterManager.nodeInstallDir().toFile());
        }
    }


}
