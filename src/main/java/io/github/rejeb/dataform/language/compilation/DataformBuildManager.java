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

import com.intellij.build.BuildContentManager;
import com.intellij.build.BuildViewManager;
import com.intellij.build.DefaultBuildDescriptor;
import com.intellij.build.FilePosition;
import com.intellij.build.events.MessageEvent;
import com.intellij.build.events.impl.*;
import com.intellij.icons.AllIcons;
import com.intellij.ide.nls.NlsMessages;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vfs.VirtualFile;
import io.github.rejeb.dataform.language.compilation.model.CompilationError;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.schema.sql.DataformTableSchemaService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;
import java.util.concurrent.Future;

public final class DataformBuildManager {

    private static final Logger LOG = Logger.getInstance(DataformBuildManager.class);
    private static final String NOTIFICATION_GROUP = "Dataform.Notifications";
    private static final String TASK_NAME = "Dataform Compile";

    private DataformBuildManager() {
    }

    @NotNull
    public static Future<DataformBuildResult> build(@NotNull Project project) {
        DataformBuildContext context = new DataformBuildContext(project);

        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            if (!context.waitAndStart()) return;

            Object buildId = context;
            long startTime = context.started;
            String workDir = getWorkDir(project);

            BuildViewManager buildViewManager = project.getService(BuildViewManager.class);

            ApplicationManager.getApplication().invokeLater(() -> {
                BuildContentManager.getInstance(project).getOrCreateToolWindow();
            });

            DataformBuildAction rebuildAction = new DataformBuildAction(context,
                    project,
                    "Rerun Dataform Compile",
                    "Rerun dataform compile",
                    AllIcons.Actions.Rebuild);
            DefaultBuildDescriptor descriptor = new DefaultBuildDescriptor(
                    buildId, TASK_NAME, workDir, startTime
            ).withRestartAction(rebuildAction);
            buildViewManager.onEvent(buildId,
                    new StartBuildEventImpl(descriptor, "Running dataform compile..."));

            try {
                DataformCompilationService service = DataformCompilationService.getInstance(project);
                CompiledGraph compiledGraph = service.compile(true);

                if (compiledGraph == null) {
                    context.errors.incrementAndGet();
                    buildViewManager.onEvent(buildId,
                            new MessageEventImpl(buildId, MessageEvent.Kind.ERROR,
                                    TASK_NAME, "dataform compile returned no output — check stderr", null));
                    finishBuild(buildViewManager, buildId, context, false, "Dataform compile failed");
                    showNotification(project, "Dataform compile failed", null, NotificationType.ERROR);
                    return;
                }

                List<CompilationError> errors = compiledGraph.getGraphErrors() != null
                        ? compiledGraph.getGraphErrors().getCompilationErrors()
                        : List.of();

                if (!errors.isEmpty()) {
                    for (CompilationError error : errors) {
                        context.errors.incrementAndGet();
                        String detail = buildErrorDetail(error);
                        FilePosition filePosition = resolveFilePosition(project, error);

                        if (filePosition != null) {
                            // Erreur cliquable avec lien vers le fichier
                            buildViewManager.onEvent(buildId,
                                    new FileMessageEventImpl(buildId, MessageEvent.Kind.ERROR,
                                            TASK_NAME, detail, detail, filePosition));
                        } else {
                            buildViewManager.onEvent(buildId,
                                    new MessageEventImpl(buildId, MessageEvent.Kind.ERROR,
                                            TASK_NAME, detail, detail));
                        }
                    }
                    finishBuild(buildViewManager, buildId, context, false,
                            "Dataform compile failed with " + errors.size() + " error(s)");
                    showNotification(project, "Dataform compile failed",
                            errors.size() + " error(s) — see Build window", NotificationType.ERROR);
                    return;
                }

                // Succès
                project.getService(DataformTableSchemaService.class)
                        .refreshAsync(compiledGraph, true);

                String durationMsg = NlsMessages.formatDuration(context.getDuration());
                finishBuild(buildViewManager, buildId, context, true, "Dataform compile succeeded");
                showNotification(project, "Dataform compile succeeded",
                        "Completed in " + durationMsg, NotificationType.INFORMATION);

            } catch (Exception e) {
                LOG.error("Unexpected error during dataform compile", e);
                context.errors.incrementAndGet();
                buildViewManager.onEvent(buildId,
                        new MessageEventImpl(buildId, MessageEvent.Kind.ERROR,
                                TASK_NAME, e.getMessage(), e.getMessage()));
                finishBuild(buildViewManager, buildId, context, false,
                        "Dataform compile error: " + e.getMessage());
                showNotification(project, "Dataform compile error", e.getMessage(), NotificationType.ERROR);
            }
        });

        return context.result;
    }

    private static void finishBuild(BuildViewManager buildViewManager,
                                    Object buildId,
                                    DataformBuildContext context,
                                    boolean success,
                                    String message) {
        context.finished(success, message);
        buildViewManager.onEvent(buildId,
                new FinishBuildEventImpl(buildId, null, System.currentTimeMillis(), message,
                        success ? new SuccessResultImpl() : new FailureResultImpl()));
    }

    /**
     * Construit le message d'erreur affiché dans le Build Tool Window.
     */
    private static String buildErrorDetail(CompilationError error) {
        StringBuilder sb = new StringBuilder();
        if (error.getActionName() != null) {
            sb.append("[").append(error.getActionName()).append("] ");
        }
        if (error.getMessage() != null) {
            sb.append(error.getMessage());
        } else if (error.getStack() != null) {
            sb.append(error.getStack());
        }
        return sb.toString().isBlank() ? "(no message)" : sb.toString();
    }

    /**
     * Résout le chemin de fichier depuis CompilationError.fileName
     * pour créer une FilePosition cliquable.
     */
    @Nullable
    private static FilePosition resolveFilePosition(@NotNull Project project,
                                                    @NotNull CompilationError error) {
        String fileName = error.getFileName();
        if (fileName == null || fileName.isBlank()) return null;

        VirtualFile projectDir = ProjectUtil.guessProjectDir(project);
        if (projectDir == null) return null;

        // fileName est relatif à la racine du projet (ex: "definitions/my_table.sqlx")
        String normalized = fileName.replace("\\", "/");
        VirtualFile vf = projectDir.findFileByRelativePath(normalized);
        if (vf == null) {
            // Essai direct comme chemin absolu
            File f = new File(normalized);
            if (f.exists()) {
                return new FilePosition(f, 0, 0);
            }
            return null;
        }

        return new FilePosition(new File(vf.getPath()), 0, 0);
    }

    @NotNull
    private static String getWorkDir(@NotNull Project project) {
        VirtualFile projectDir = ProjectUtil.guessProjectDir(project);
        return projectDir != null ? projectDir.getPath() : project.getBasePath() != null
                                                           ? project.getBasePath() : "";
    }

    private static void showNotification(@NotNull Project project,
                                         @NotNull String title,
                                         @Nullable String content,
                                         @NotNull NotificationType type) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup(NOTIFICATION_GROUP)
                .createNotification(title, content != null ? content : "", type)
                .notify(project);
    }
}