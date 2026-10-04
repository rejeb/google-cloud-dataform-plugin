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

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import io.github.rejeb.dataform.language.compilation.DataformCompilationService;
import io.github.rejeb.dataform.language.diagnostics.DataformEditorRefresher;
import io.github.rejeb.dataform.language.util.DataformNotifications;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

public final class DataformPackageInstallerImpl implements DataformPackageInstaller {

    private static final Logger LOG = Logger.getInstance(DataformPackageInstallerImpl.class);
    private static final int INSTALL_TIMEOUT_MS = 10 * 60 * 1000;
    private static final int MAX_OUTPUT_CHARS = 1_000;

    private final Project project;

    public DataformPackageInstallerImpl(@NotNull Project project) {
        this.project = project;
    }

    @Override
    public void installAsync(@NotNull VirtualFile projectDir) {
        new Task.Backgroundable(project, "Dataform: installing packages", true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setIndeterminate(true);
                install(projectDir, indicator);
            }
        }.queue();
    }

    private void install(@NotNull VirtualFile projectDir, @NotNull ProgressIndicator indicator) {
        Optional<GeneralCommandLine> command = project.getService(DataformInterpreterManager.class)
                .buildDataformCommand(List.of("install"));
        if (command.isEmpty()) {
            notify("Dataform packages not installed",
                    "Node.js is not configured. Configure it, then run <code>dataform install</code> in the project directory.",
                    NotificationType.WARNING, projectDir, false);
            return;
        }

        GeneralCommandLine cmd = command.get()
                .withWorkDirectory(projectDir.getPath())
                .withCharset(StandardCharsets.UTF_8);
        try {
            ProcessOutput output = new CapturingProcessHandler(cmd)
                    .runProcessWithProgressIndicator(indicator, INSTALL_TIMEOUT_MS);
            if (output.isCancelled()) {
                return;
            }
            if (output.isTimeout() || output.getExitCode() != 0) {
                LOG.warn("dataform install failed (timeout=" + output.isTimeout()
                        + ", exitCode=" + output.getExitCode() + "): " + output.getStderr());
                notify("Dataform packages not installed",
                        StringUtil.escapeXmlEntities(failureDetails(output)),
                        NotificationType.ERROR, projectDir, true);
                return;
            }
            VfsUtil.markDirtyAndRefresh(true, true, true, projectDir);
            notify("Dataform packages installed", "", NotificationType.INFORMATION, projectDir, false);
            recompile(indicator);
        } catch (Exception e) {
            LOG.warn("Unable to run dataform install", e);
            notify("Dataform packages not installed",
                    StringUtil.escapeXmlEntities(StringUtil.notNullize(e.getMessage())),
                    NotificationType.ERROR, projectDir, true);
        }
    }

    /**
     * Compiles again once {@code @dataform/core} is installed: a compilation run before, by the
     * project startup or by the edit that triggered the install, could not find it and left no graph.
     */
    private void recompile(@NotNull ProgressIndicator indicator) {
        if (project.isDisposed()) {
            return;
        }
        indicator.setText("Dataform: compiling");
        DataformCompilationService.getInstance(project).compile(true);
        DataformEditorRefresher.refresh(project);
    }

    @NotNull
    private static String failureDetails(@NotNull ProcessOutput output) {
        if (output.isTimeout()) {
            return "dataform install timed out.";
        }
        String details = output.getStderr().isBlank() ? output.getStdout() : output.getStderr();
        return StringUtil.last(details.trim(), MAX_OUTPUT_CHARS, true).toString();
    }

    private void notify(@NotNull String title,
                        @NotNull String content,
                        @NotNull NotificationType type,
                        @NotNull VirtualFile projectDir,
                        boolean retry) {
        if (project.isDisposed()) {
            return;
        }
        Notification notification = DataformNotifications.create(title, content, type);
        if (retry) {
            notification.addAction(NotificationAction.createSimpleExpiring("Retry", () -> installAsync(projectDir)));
        }
        notification.notify(project);
    }
}
