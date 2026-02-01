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
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;


public class DataformInstaller implements ProjectActivity {
    private static final Logger LOG = Logger.getInstance(DataformInstaller.class);
    private static final String NOTIFICATION_GROUP_ID = "Dataform.Notifications";


    @Override
    public @Nullable Object execute(@NonNull Project project, @NonNull Continuation<? super Unit> continuation) {
        NodeInterpreterManager nodeInterpreterManager = NodeInterpreterManager.getInstance(project);
        if (nodeInterpreterManager.getNodeInstallDir() != null) {
            DataformInterpreterManager dataformInterpreterManager = DataformInterpreterManager.getInstance(project);
            if (dataformInterpreterManager.dataformCorePath().isEmpty()) {
               return installDataformLibs(project, nodeInterpreterManager);
            }
        }
        return false;
    }

    private boolean installDataformLibs(Project project, NodeInterpreterManager nodeInterpreterManager) {
        LOG.debug("Installing Dataform CLI...");
        if (nodeInterpreterManager.getNodeInstallDir() == null) {
            showErrorNotification(project, "npm not found. Please install Node.js first.");
            return false;
        } else {
            try {
                return doInstall(project, nodeInterpreterManager);
            } catch (Exception e) {
                LOG.error("Installation failed", e);
                showErrorNotification(project, "Error installing Dataform CLI: " + e.getMessage());
                return false;
            }
        }
    }

    private Boolean doInstall(Project project, NodeInterpreterManager nodeInterpreterManager) throws Exception {
        return ProgressManager.getInstance()
                .run(new Task.WithResult<Boolean, Exception>(project,
                        "Installing Dataform CORE/CLI", false) {
                    @Override
                    protected Boolean compute(@NotNull ProgressIndicator indicator) throws Exception {
                        indicator.setText("Installing Dataform CLI and Core via npm...");
                        indicator.setIndeterminate(true);

                        File npmFile = nodeInterpreterManager.getNpmExecutable().toFile();
                        File nodeBinDir = nodeInterpreterManager.getNodeInstallDir().resolve("bin").toFile();
                        File nodeModulesDir = nodeInterpreterManager.getNodeInstallDir().toFile();

                        ProcessBuilder pb = new ProcessBuilder(
                                npmFile.getAbsolutePath(),
                                "install",
                                "@dataform/cli",
                                "@dataform/core",
                                "typescript",
                                "-g",
                                "--prefix",
                                nodeModulesDir.getAbsolutePath()
                        );

                        String pathEnv = nodeBinDir.getAbsolutePath() + File.pathSeparator +
                                System.getenv("PATH");
                        pb.environment().put("PATH", pathEnv);

                        pb.redirectErrorStream(true);
                        Process process = pb.start();


                        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                        String line;
                        while ((line = reader.readLine()) != null) {
                            LOG.debug(line);
                            indicator.setText2(line);
                        }

                        int exitCode = process.waitFor();

                        if (exitCode != 0) {
                            process.errorReader().lines().forEach(System.err::println);
                            showErrorNotification(project, "Failed to install Dataform CLI and Core. Exit code: " + exitCode);
                        }
                        return true;

                    }
                });
    }

    private void showErrorNotification(Project project, String message) {
        ApplicationManager.getApplication().invokeLater(() -> {
            NotificationGroup group = NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP_ID);
            Notification notification = group.createNotification(
                    "Error",
                    message,
                    NotificationType.ERROR
            );
            notification.notify(project);
        });
    }

}
