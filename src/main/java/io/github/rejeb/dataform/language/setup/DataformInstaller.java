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
package io.github.rejeb.dataform.language.setup;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationActivationListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.util.messages.MessageBusConnection;

import io.github.rejeb.dataform.language.settings.DataformToolsConfigurable;
import io.github.rejeb.dataform.language.settings.DataformToolsSettings;
import io.github.rejeb.dataform.language.util.NodeJsNpmUtils;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;


public class DataformInstaller implements ProjectActivity {

    private static final Logger LOG = Logger.getInstance(DataformInstaller.class);
    private static final AtomicBoolean dataformNotificationShown = new AtomicBoolean(false);
    private static final AtomicBoolean nodeJsNotificationShown = new AtomicBoolean(false);

    @Override
    public @Nullable Object execute(@NotNull Project project,
                                    @NotNull Continuation<? super Unit> continuation) {

        ApplicationManager.getApplication().invokeLater(() -> {
            checkAndSetup(project);
            MessageBusConnection connection = ApplicationManager.getApplication()
                    .getMessageBus()
                    .connect();

            connection.subscribe(ApplicationActivationListener.TOPIC,
                    new ApplicationActivationListener() {
                        @Override
                        public void applicationActivated(@NotNull IdeFrame ideFrame) {
                            DataformToolsSettings settings = DataformToolsSettings.getInstance();
                            if (!settings.getCliExecutablePath().isBlank()
                                    && !settings.getCoreInstallPath().isBlank()) {
                                connection.disconnect();
                                return;
                            }
                            checkAndSetup(project);
                            connection.disconnect();
                            connection.dispose();
                        }
                    });
        });

        return null;
    }


    public static void checkAndSetup(@NotNull Project project) {
        DataformToolsSettings settings = DataformToolsSettings.getInstance();
        NodeInterpreterManager nim = NodeInterpreterManager.getInstance(project);
        if (nim.npmExecutable() == null) {
            LOG.info("Node.js not configured — notifying user.");
            settings.update("", "", "", "", "");
            if (nodeJsNotificationShown.compareAndSet(false, true)) {
                NodeJsNpmUtils.showNpmConfigurationDialog(project);
            }
            return;
        }

        Optional<Path> dataformRootDir = findDataformLibRootDir(nim);
        if (dataformRootDir.isEmpty()) {
            LOG.info("Dataform not found — notifying user.");
            if (dataformNotificationShown.compareAndSet(false, true)) {
                showConfigureDataformNotification(project);
            }
            return;
        }


        if (settings.getCoreInstallPath().isBlank()
                || settings.getCliExecutablePath().isBlank()) {
            Path root = dataformRootDir.get();
            String core = root.resolve("core").toAbsolutePath().toString();
            String cli = resolveCli(nim.nodeBinDir());
            settings.update(cli, core, "", "", "");
            LOG.info("Dataform paths persisted — core: " + core + ", cli: " + cli);
        }
    }

    public static Optional<Path> findDataformLibRootDir(@NotNull NodeInterpreterManager nim) {
        Path nodeModulesDir = nim.nodeModulesDir();
        if (nodeModulesDir == null) return Optional.empty();
        Path root = nodeModulesDir.resolve("@dataform");
        if (root.resolve("core").toFile().exists()
                && root.resolve("cli").toFile().exists()) {
            return Optional.of(root);
        }
        return Optional.empty();
    }

    private static String resolveCli(@Nullable Path nodeBinDir) {
        if (nodeBinDir == null) return "";
        String exe = SystemInfo.isWindows ? "dataform.cmd" : "dataform";
        return nodeBinDir.resolve(exe).toAbsolutePath().toString();
    }

    private static void showConfigureDataformNotification(@NotNull Project project) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup("Dataform.Notifications")
                .createNotification(
                        "Dataform not configured",
                        "Dataform CLI and Core were not found. Please configure their paths.",
                        NotificationType.WARNING)
                .addAction(new NotificationAction("Configure") {
                    @Override
                    public void actionPerformed(@NotNull AnActionEvent e,
                                                @NotNull Notification notification) {
                        ShowSettingsUtil.getInstance()
                                .showSettingsDialog(project, DataformToolsConfigurable.class);
                        notification.expire();
                    }
                })
                .notify(project);
    }

}
