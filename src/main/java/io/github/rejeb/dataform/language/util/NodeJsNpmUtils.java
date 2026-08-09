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
package io.github.rejeb.dataform.language.util;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.util.ExecUtil;
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreter;
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterManager;
import com.intellij.javascript.nodejs.npm.NpmManager;
import com.intellij.javascript.nodejs.settings.NodeSettingsConfigurable;
import com.intellij.javascript.nodejs.util.NodePackage;
import com.intellij.notification.*;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.Optional;


public class NodeJsNpmUtils {

    private static final @NotNull Logger LOGGER = Logger.getInstance(NodeJsNpmUtils.class);
    private static final int NPM_PREFIX_TIMEOUT_MS = 5_000;

    /**
     * Resolves the Node.js installation directory, querying {@code npm config get prefix} only when
     * called outside the EDT and outside a read action. In those contexts the interpreter-based
     * fallback is used instead, because spawning npm can block for several seconds (especially on
     * Windows) and blocking a read action stalls every write action behind it.
     */
    public static Optional<Path> findNodeInstallDir(Project project, Path npmExecutable) {
        Optional<Path> systemPath = queryNpmPrefix(npmExecutable);
        return systemPath.isPresent() ? systemPath : interpreterInstallDir(project);
    }

    private static Optional<Path> queryNpmPrefix(Path npmExecutable) {
        Application application = ApplicationManager.getApplication();
        if (application.isDispatchThread()) {
            LOGGER.warn("Skipping 'npm config get prefix' on EDT, falling back to the configured interpreter");
            return Optional.empty();
        }
        if (application.isReadAccessAllowed()) {
            LOGGER.warn("Skipping 'npm config get prefix' under a read action, "
                    + "falling back to the configured interpreter");
            return Optional.empty();
        }
        GeneralCommandLine cmd = new GeneralCommandLine(npmExecutable.toFile().getAbsolutePath(), "config", "get", "prefix");
        try {
            ProcessOutput output = ExecUtil.execAndGetOutput(cmd, NPM_PREFIX_TIMEOUT_MS);
            if (output.isTimeout() || output.getExitCode() != 0) {
                LOGGER.warn("'npm config get prefix' failed (timeout=" + output.isTimeout()
                        + ", exitCode=" + output.getExitCode() + ")");
                return Optional.empty();
            }
            return output.getStdoutLines().stream()
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .findFirst()
                    .map(Path::of);
        } catch (Exception e) {
            LOGGER.warn("Unable to resolve the npm prefix", e);
            return Optional.empty();
        }
    }

    private static Optional<Path> interpreterInstallDir(Project project) {
        Optional<File> nodeExecutableDir = Optional
                .ofNullable(NodeJsInterpreterManager.getInstance(project).getInterpreter())
                .map(NodeJsInterpreter::getReferenceName)
                .map(File::new)
                .map(File::getParentFile);
        if (SystemInfo.isWindows) {
            return nodeExecutableDir.map(File::toPath);
        }
        return nodeExecutableDir
                .map(File::getParentFile)
                .map(File::toPath);
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

    public static InstallResult installNodeJsLib(String libName,
                                                 File npmFile, File nodeBinDir,
                                                 File nodeInstallDir) {
        try {
            LOGGER.info("Installing " + libName + "...");

            ProcessBuilder pb = new ProcessBuilder(
                    npmFile.getAbsolutePath(),
                    "install", libName, "-g",
                    "--prefix", nodeInstallDir.getAbsolutePath()
            );
            pb.environment().put("PATH",
                    nodeBinDir.getAbsolutePath() + File.pathSeparator +
                            System.getenv("PATH"));
            pb.redirectErrorStream(true);

            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    LOGGER.debug(line);
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                return InstallResult.error(
                        "npm exited with code " + exitCode + ":\n" + output);
            }

            LOGGER.info(libName + " installed successfully.");
            return InstallResult.ok();

        } catch (Exception e) {
            LOGGER.error("Error installing " + libName, e);
            return InstallResult.error(e.getMessage());
        }
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
        ShowSettingsUtil.getInstance().showSettingsDialog(
                project,
                NodeSettingsConfigurable.class
        );
    }

}

