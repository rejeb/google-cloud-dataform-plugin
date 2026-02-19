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

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
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

            DataformInterpreterManager dataformInterpreterManager = project.getService(DataformInterpreterManager.class);
            if (dataformInterpreterManager.dataformCorePath().isEmpty() && notifyUserDataformCore) {
                notifyUserDataformCore = false;
                installDataformCore(project);
            }

            if (dataformInterpreterManager.dataformCliDir().isEmpty() && notifyUserDataformCli) {
                notifyUserDataformCli = false;
                installDataformCli(project);
            }

        });
        return project.getService(DataformInterpreterManager.class).dataformCorePath().isPresent();
    }

    private void installDataformCli(Project project) {
        LOG.debug("Installing Dataform CLI...");
        NotificationGroupManager.getInstance()
                .getNotificationGroup("Dataform.Notifications")
                .createNotification("Dataform CLI installation requested",
                        "Dataform CLI is not installed. Would you like to install it?",
                        NotificationType.INFORMATION)
                .addAction(new NotificationAction("Install CLI") {
                               @Override
                               public void actionPerformed(@NotNull AnActionEvent e,
                                                           @NotNull Notification notification) {
                                   NodeInterpreterManager nodeInterpreterManager = NodeInterpreterManager.getInstance(project);
                                   if (nodeInterpreterManager.npmExecutable() != null &&
                                           nodeInterpreterManager.nodeBinDir() != null &&
                                           nodeInterpreterManager.nodeInstallDir() != null) {
                                       NodeJsNpmUtils.installNodeJsLib("@dataform/cli",
                                               project,
                                               nodeInterpreterManager.npmExecutable().toFile(),
                                               nodeInterpreterManager.nodeBinDir().toFile(),
                                               nodeInterpreterManager.nodeInstallDir().toFile());
                                   }
                               }
                           }
                ).notify(project);
    }

    private void installDataformCore(Project project) {
        LOG.debug("Installing Dataform CORE...");
        NotificationGroupManager.getInstance()
                .getNotificationGroup("Dataform.Notifications")
                .createNotification("Dataform Core installation requested",
                        "Dataform Core is not installed. Would you like to install it?",
                        NotificationType.INFORMATION)
                .addAction(new NotificationAction("Install CORE") {
                               @Override
                               public void actionPerformed(@NotNull AnActionEvent e,
                                                           @NotNull Notification notification) {
                                   NodeInterpreterManager nodeInterpreterManager = NodeInterpreterManager.getInstance(project);
                                   if (nodeInterpreterManager.npmExecutable() != null &&
                                           nodeInterpreterManager.nodeBinDir() != null &&
                                           nodeInterpreterManager.nodeInstallDir() != null) {
                                       NodeJsNpmUtils.installNodeJsLib("@dataform/core",
                                               project,
                                               nodeInterpreterManager.npmExecutable().toFile(),
                                               nodeInterpreterManager.nodeBinDir().toFile(),
                                               nodeInterpreterManager.nodeInstallDir().toFile());
                                   }
                               }
                           }
                ).

                notify(project);
    }


}
