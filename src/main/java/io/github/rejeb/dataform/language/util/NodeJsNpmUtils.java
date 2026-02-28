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
package io.github.rejeb.dataform.language.util;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.util.ExecUtil;
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreter;
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterManager;
import com.intellij.javascript.nodejs.npm.NpmManager;
import com.intellij.javascript.nodejs.util.NodePackage;
import com.intellij.notification.*;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.Optional;


public class NodeJsNpmUtils {

    private static final @NotNull Logger LOGGER = Logger.getInstance(NodeJsNpmUtils.class);
    private static final String NOTIFICATION_GROUP_ID = "Dataform.Notifications";

    public static Optional<Path> findNodeInstallDir(Project project, Path npmExecutable) {
        GeneralCommandLine cmd = new GeneralCommandLine(npmExecutable.toFile().getAbsolutePath(), "config", "get", "prefix");
        Optional<Path> systemPath = Optional.ofNullable(ExecUtil.execAndReadLine(cmd))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Path::of);
        if (systemPath.isEmpty()) {
            Optional<File> nodeExecutableDir = Optional
                    .ofNullable(NodeJsInterpreterManager.getInstance(project).getInterpreter())
                    .map(NodeJsInterpreter::getReferenceName)
                    .map(File::new)
                    .map(File::getParentFile);
            if (SystemInfo.isWindows) {
                return nodeExecutableDir
                        .map(File::toPath);
            } else {
                return nodeExecutableDir
                        .map(File::getParentFile)
                        .map(File::toPath);
            }
        } else {
            return systemPath;
        }
    }


    public static Optional<Path> findValidNpmPath(Project project) {
        try {
            NpmManager npmManager = NpmManager.getInstance(project);
            Optional<NodePackage> npmPackage = Optional.ofNullable(npmManager.getPackage());
            return npmPackage.map(NodePackage::getSystemDependentPath).filter(path -> !path.isEmpty()).map(Path::of);
        } catch (Exception e) {
            LOGGER.error("Error retrieving npm path", e);
            return Optional.empty();
        }
    }

    public static void installNodeJsLib(String libName, Project project, File npmFile, File nodeBinDir, File nodeModulesDir) {

        ProgressManager.getInstance()
                .run(new Task.Backgroundable(project,
                        "Installing " + libName, false) {
                    @Override
                    public void run(@NotNull ProgressIndicator indicator) {
                        try {
                            indicator.setText("Installing " + libName + " via npm...");
                            indicator.setIndeterminate(true);


                            ProcessBuilder pb = new ProcessBuilder(
                                    npmFile.getAbsolutePath(),
                                    "install",
                                    libName,
                                    "-g",
                                    "--prefix",
                                    nodeModulesDir.getAbsolutePath()
                            );

                            String pathEnv = nodeBinDir.getAbsolutePath() + File.pathSeparator +
                                    System.getenv("PATH");
                            pb.environment().put("PATH", pathEnv);

                            pb.redirectErrorStream(true);
                            Process process = null;
                            try {
                                process = pb.start();
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }

                            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                            String line;
                            while ((line = reader.readLine()) != null) {
                                LOGGER.debug(line);
                                indicator.setText2(line);
                            }

                            int exitCode = process.waitFor();

                            if (exitCode != 0) {
                                process.errorReader().lines().forEach(System.err::println);
                                showErrorNotification(project, "Failed to install " + libName);
                            }
                        } catch (Exception e) {
                            LOGGER.error("Error installing " + libName + " via npm", e);
                        }
                    }
                });

    }


    public static Optional<Path> getGlobalNodeModulesPath(Path nodeInstallDir) {
        String nodeModulesDir = SystemInfo.isWindows ? "node_modules" : "lib/node_modules";
        return Optional.of(nodeInstallDir.resolve(nodeModulesDir)).filter(path -> path.toFile().exists());
    }

    public static void showNpmConfigurationDialog(Project project) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup("Dataform.Notifications")
                .createNotification("Npm not available",
                        "Npm is not configured.\n\nwould you like to open the settings?",
                        NotificationType.INFORMATION)
                .addAction(new NotificationAction("Configure nodeJs") {
                    @Override
                    public void actionPerformed(@NotNull AnActionEvent e,
                                                @NotNull Notification notification) {
                        openNodeJsSettings(project);
                        notification.expire();
                    }
                })
                .notify(project);
    }


    private static void openNodeJsSettings(Project project) {
        ShowSettingsUtil.getInstance().showSettingsDialog(project, "Settings.JavaScript.Node.js");
    }

    private static void showErrorNotification(Project project, String message) {
        NotificationGroup group = NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP_ID);
        Notification notification = group.createNotification(
                "Error",
                message,
                NotificationType.ERROR
        );
        notification.notify(project);
    }

}

