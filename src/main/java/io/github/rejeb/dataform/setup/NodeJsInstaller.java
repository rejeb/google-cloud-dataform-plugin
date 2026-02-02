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

import com.intellij.execution.ExecutionException;
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterManager;
import com.intellij.javascript.nodejs.interpreter.local.NodeJsLocalInterpreter;
import com.intellij.notification.*;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.system.CpuArch;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class NodeJsInstaller implements ProjectActivity {
    private static final Logger LOG = Logger.getInstance(NodeJsInstaller.class);
    private static final String NODE_BASE_URL = "https://nodejs.org/dist";
    public static final String NODE_JS_VERSION = "24.12.0";


    @Override
    public @Nullable Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
        NodeInterpreterManager nodeInterpreterManager = NodeInterpreterManager.getInstance(project);
        if (nodeInterpreterManager.nodeInstallDir() == null) {
            return installAndConfigure(project);
        }
        return null;
    }

    public boolean installAndConfigure(Project project) {

        try {
            return ProgressManager.getInstance().run(new Task.WithResult<Boolean, Exception>(
                    project,
                    "Installing Node.js " + NODE_JS_VERSION,
                    false
            ) {
                @Override
                protected Boolean compute(@NotNull ProgressIndicator indicator) throws Exception {
                    try {
                        indicator.setText(String.format("Downloading Node.js %s...", NODE_JS_VERSION));
                        indicator.setFraction(0.1);

                        Path nodePath = downloadAndExtractNodeJs(indicator);

                        indicator.setText("Configuring Node.js interpreter...");
                        indicator.setFraction(0.8);

                        configureAsDefault(nodePath, project);

                        indicator.setText("Node.js installation complete");
                        indicator.setFraction(1.0);

                        LOG.info(String.format("Successfully installed Node.js %s at %s ", NODE_JS_VERSION, nodePath));
                        showRestartNotification(project);
                        return true;
                    } catch (Exception e) {
                        LOG.error(String.format("Failed to install Node.js %s", NODE_JS_VERSION), e);
                        throw e;
                    }
                }
            });
        } catch (Exception e) {
            LOG.error("Installation failed", e);
            return false;
        }
    }


    private Path downloadAndExtractNodeJs(
            @NotNull ProgressIndicator indicator
    ) throws IOException, ExecutionException {
        String downloadUrl = buildDownloadUrl();
        Path archiveFile = Files.createTempFile(String.format("nodejs-%s", NODE_JS_VERSION), getArchiveExtension());

        try {
            indicator.setText2("Downloading from " + downloadUrl);
            downloadFile(downloadUrl, archiveFile, indicator);

            indicator.setText("Extracting node.js...");
            indicator.setFraction(0.5);
            Path installPath = getUserInstallPath();
            extractArchive(archiveFile, installPath);

            return findNodeExecutable(installPath);
        } finally {
            Files.deleteIfExists(archiveFile);
        }
    }

    private String buildDownloadUrl() {
        String versionWithV = String.format("v%s", NODE_JS_VERSION);

        String platform;
        if (SystemInfo.isWindows) {
            platform = "win";
        } else if (SystemInfo.isMac) {
            platform = "darwin";
        } else if (SystemInfo.isLinux) {
            platform = "linux";
        } else {
            throw new UnsupportedOperationException("Unsupported platform");
        }

        String arch = CpuArch.isArm64() ? "arm64" : "x64";
        String extension = SystemInfo.isWindows ? "zip" : "tar.gz";
        String filename = String.format("node-%s-%s-%s.%s", versionWithV, platform, arch, extension);

        return String.format("%s/%s/%s", NODE_BASE_URL, versionWithV, filename);
    }

    private void downloadFile(
            @NotNull String url,
            @NotNull Path destination,
            @NotNull ProgressIndicator indicator
    ) throws IOException, ExecutionException {
        URLConnection connection = new URL(url).openConnection();
        long fileSize = connection.getContentLengthLong();

        try (ReadableByteChannel rbc = Channels.newChannel(connection.getInputStream());
             FileOutputStream fos = new FileOutputStream(destination.toFile())) {

            long downloadedBytes = 0;
            java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(8192);

            while (rbc.read(buffer) != -1) {
                buffer.flip();
                downloadedBytes += buffer.remaining();
                fos.getChannel().write(buffer);
                buffer.clear();

                if (fileSize > 0) {
                    double progress = 0.1 + (downloadedBytes / (double) fileSize) * 0.4;
                    indicator.setFraction(progress);
                }

                if (indicator.isCanceled()) {
                    throw new ExecutionException("Download cancelled by user");
                }
            }
        }
    }

    private void extractArchive(@NotNull Path archiveFile, @NotNull Path targetPath)
            throws IOException, ExecutionException {
        Files.createDirectories(targetPath);

        if (SystemInfo.isWindows) {
            extractZip(archiveFile, targetPath);
        } else {
            extractTarGz(archiveFile, targetPath);
        }
    }

    private void extractZip(@NotNull Path zipFile, @NotNull Path targetDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path filePath = targetDir.resolve(entry.getName());

                if (entry.isDirectory()) {
                    Files.createDirectories(filePath);
                } else {
                    Files.createDirectories(filePath.getParent());
                    Files.copy(zis, filePath, StandardCopyOption.REPLACE_EXISTING);
                    if (!SystemInfo.isWindows && (entry.getName().contains("/bin/") ||
                            entry.getName().endsWith("node"))) {
                        filePath.toFile().setExecutable(true);
                    }
                }
                zis.closeEntry();
            }
        }
    }

    private void extractTarGz(@NotNull Path tarFile, @NotNull Path targetDir)
            throws IOException, ExecutionException {
        ProcessBuilder pb = new ProcessBuilder()
                .command("tar", "-xzf", tarFile.toString(), "-C", targetDir.toString())
                .redirectErrorStream(true);
        Process process = pb.start();
        try {
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                StringBuilder error = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        error.append(line).append("\n");
                    }
                }
                throw new ExecutionException("Failed to extract tar.gz: " + error);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExecutionException("Extraction interrupted", e);
        }
    }

    private Path findNodeExecutable(@NotNull Path installPath) throws ExecutionException, IOException {
        String nodeExe = SystemInfo.isWindows ? "node.exe" : "node";
        List<Path> possiblePaths = Arrays.asList(
                installPath.resolve("bin").resolve(nodeExe),
                installPath.resolve(nodeExe)
        );

        for (Path path : possiblePaths) {
            if (Files.exists(path) && Files.isExecutable(path)) {
                return path;
            }
        }

        try (Stream<Path> stream = Files.list(installPath)) {
            Path found = stream
                    .filter(Files::isDirectory)
                    .flatMap(subdir -> Stream.of(
                            subdir.resolve("bin").resolve(nodeExe),
                            subdir.resolve(nodeExe)
                    ))
                    .filter(path -> Files.exists(path) && Files.isExecutable(path))
                    .findFirst()
                    .orElse(null);

            if (found != null) {
                return found;
            }
        }

        throw new ExecutionException("Node executable not found in " + installPath);
    }

    private void configureAsDefault(@NotNull Path nodePath, @NotNull Project project) {
        ApplicationManager.getApplication().invokeAndWait(() ->
                        ApplicationManager.getApplication().runWriteAction(() -> {
                            NodeJsInterpreterManager interpreterManager =
                                    NodeJsInterpreterManager.getInstance(project);

                            NodeJsLocalInterpreter interpreter =
                                    new NodeJsLocalInterpreter(nodePath.toString());

                            interpreterManager.setInterpreterRef(
                                    interpreter.toRef()
                            );
                            LOG.info(String.format("Configured Node.js interpreter: %s", nodePath));
                        })
                , ModalityState.defaultModalityState());
    }

    public static Path getUserInstallPath() {
        String userHome = System.getProperty("user.home");
        return Paths.get(userHome, ".nodejs", String.format("node-%s", NODE_JS_VERSION));
    }

    private String getArchiveExtension() {
        return SystemInfo.isWindows ? ".zip" : ".tar.gz";
    }

    private void showRestartNotification(Project project) {
        NotificationGroup notificationGroup = NotificationGroupManager.getInstance()
                .getNotificationGroup("Dataform.Notifications");

        Notification notification = notificationGroup.createNotification(
                "Node.js installation complete",
                "Node.js " + NODE_JS_VERSION + " has been installed successfully. " +
                        "A restart is required for the changes to take effect.",
                NotificationType.INFORMATION
        );

        notification.addAction(new NotificationAction("Restart now") {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
                ApplicationManager.getApplication().restart();
            }
        });

        notification.addAction(new NotificationAction("Restart later") {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
                notification.expire();
            }
        });

        notification.notify(project);
    }
}