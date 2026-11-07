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
package io.github.rejeb.dataform.language.compilation;

import com.intellij.build.*;
import com.intellij.build.events.FinishBuildEvent;
import com.intellij.build.events.MessageEvent;
import com.intellij.build.events.OutputBuildEvent;
import com.intellij.build.events.StartBuildEvent;
import com.intellij.build.events.impl.FailureResultImpl;
import com.intellij.build.events.impl.SuccessResultImpl;
import com.intellij.execution.process.ProcessOutputType;
import com.intellij.icons.AllIcons;
import com.intellij.ide.nls.NlsMessages;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.util.text.LineColumn;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.ThreeState;
import io.github.rejeb.dataform.language.compilation.model.CompilationError;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.diagnostics.compile.BuildErrorPlacement;
import io.github.rejeb.dataform.language.diagnostics.sql.bigquery.DryRunBuildProblem;
import io.github.rejeb.dataform.language.diagnostics.sql.bigquery.DryRunBuildProblems;
import io.github.rejeb.dataform.language.schema.sql.DataformTableSchemaService;
import io.github.rejeb.dataform.language.schema.sql.SchemaRefreshOutcome;
import io.github.rejeb.dataform.language.util.DataformNotifications;
import io.github.rejeb.dataform.language.util.DataformPaths;
import io.github.rejeb.dataform.language.util.DataformProjectLayout;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public final class DataformBuildManager {

    private static final Logger LOG = Logger.getInstance(DataformBuildManager.class);
    private static final String TASK_NAME = "Dataform Compile";

    private DataformBuildManager() {
    }

    /**
     * Starts a Dataform compilation on a pooled thread and reports it in the Build tool window.
     *
     * @return a future completed when the build finishes or is canceled
     */
    @NotNull
    public static CompletableFuture<DataformBuildResult> build(@NotNull Project project) {
        DataformBuildContext context = new DataformBuildContext(project);

        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            if (!context.waitAndStart()) return;

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
            DefaultBuildDescriptor descriptor = descriptor(context, workDir, startTime)
                    .withRestartAction(rebuildAction);

            buildViewManager.onEvent(context,
                    StartBuildEvent.builder("Running dataform compile...", descriptor).build());

            try {
                DataformCompilationService service = DataformCompilationService.getInstance(project);
                CompiledGraph compiledGraph = service.compile(true);
                CliRun run = service.lastCliRun();
                CliFailure failure = run != null ? CliFailureExplainer.explain(run) : null;
                if (failure != null) {
                    reportCliFailure(project, buildViewManager, context, run, failure, compiledGraph);
                    return;
                }

                if (compiledGraph == null) {
                    reportError(buildViewManager, context, "dataform compile returned no output — check stderr",
                            BuildErrorText.details("dataform compile returned no output.\n"
                                    + "Run the compile command in a terminal to see what the Dataform CLI reports."), null);
                    finishBuild(buildViewManager, context, false, "Dataform compile failed");
                    showNotification(project, "Dataform compile failed", null, NotificationType.ERROR);
                    return;
                }

                List<CompilationError> errors = CompilationFailures.errorsOf(compiledGraph);
                for (CompilationError error : errors) {
                    PlacedError placed = ReadAction.nonBlocking(() -> BuildErrorPlacement.place(project, error))
                            .executeSynchronously();
                    reportError(buildViewManager, context, BuildErrorText.title(error),
                            BuildErrorText.placedDetails(error, placed), filePosition(project, error, placed));
                }

                String schemaCheck = null;
                int failedQueries = 0;
                if (!CompilationFailures.isTotalFailure(compiledGraph)) {
                    Set<String> failedFiles = CompilationFailures.fileNamesOf(compiledGraph);
                    SchemaRefreshOutcome outcome = awaitSchemaRefresh(project, compiledGraph, failedFiles);
                    List<DryRunBuildProblem> problems = outcome.extracted()
                            ? ReadAction.nonBlocking(() -> DryRunBuildProblems.collect(project, compiledGraph, failedFiles))
                            .executeSynchronously()
                            : List.of();
                    problems.forEach(problem -> reportDryRunProblem(buildViewManager, context, problem));
                    failedQueries = problems.size();
                    schemaCheck = SchemaCheckText.of(outcome, failedQueries);
                }

                buildViewManager.onEvent(context, output(context, BuildSummaryText.header(run)
                        + BuildSummaryText.of(compiledActions(compiledGraph), errors.size(),
                        CompilationFailures.fileNamesOf(compiledGraph).size(), schemaCheck)));

                String compileResult = errors.isEmpty()
                        ? "Dataform compile succeeded"
                        : "Dataform compile failed with " + errors.size() + " error(s)";
                finishBuild(buildViewManager, context, errors.isEmpty(), errors.isEmpty() && failedQueries == 0,
                        schemaCheck == null ? compileResult : compileResult + ", " + schemaCheck);
                if (!errors.isEmpty()) {
                    showNotification(project, "Dataform compile failed",
                            errors.size() + " error(s) — see Build window", NotificationType.ERROR);
                } else if (failedQueries > 0) {
                    showNotification(project, "Dataform compile succeeded",
                            schemaCheck + " — see Build window", NotificationType.WARNING);
                } else {
                    String durationMsg = NlsMessages.formatDuration(context.getDuration());
                    showNotification(project, "Dataform compile succeeded",
                            "Completed in " + durationMsg, NotificationType.INFORMATION);
                }

            } catch (Exception e) {
                LOG.warn("Unexpected error during dataform compile", e);
                String title = e.getMessage() != null && !e.getMessage().isBlank()
                        ? e.getMessage() : e.getClass().getSimpleName();
                reportError(buildViewManager, context, title,
                        BuildErrorText.details(ExceptionUtil.getThrowableText(e)), null);
                finishBuild(buildViewManager, context, false,
                        "Dataform compile error: " + title);
                showNotification(project, "Dataform compile error", title, NotificationType.ERROR);
            }
        });

        return context.result;
    }

    /**
     * The descriptor of a build. The errors are listed without opening the file of the first one:
     * the user decides which to look at.
     */
    @NotNull
    static DefaultBuildDescriptor descriptor(@NotNull Object buildId, @NotNull String workDir, long startTime) {
        DefaultBuildDescriptor descriptor = new DefaultBuildDescriptor(buildId, TASK_NAME, workDir, startTime);
        descriptor.setNavigateToError(ThreeState.NO);
        return descriptor;
    }

    private static void finishBuild(BuildViewManager buildViewManager,
                                    DataformBuildContext context,
                                    boolean success,
                                    String message) {
        finishBuild(buildViewManager, context, success, success, message);
    }

    private static void finishBuild(BuildViewManager buildViewManager,
                                    DataformBuildContext context,
                                    boolean compiled,
                                    boolean shownAsSuccess,
                                    String message) {
        context.finished(compiled, message);
        buildViewManager.onEvent(context,
                FinishBuildEvent.builder(context, message,
                                shownAsSuccess ? new SuccessResultImpl() : new FailureResultImpl())
                        .withParentId(context)
                        .withTime(System.currentTimeMillis())
                        .build());
    }

    /**
     * Waits for the schema refresh of the graph, which dry-runs every action query, so that the
     * build reports the queries BigQuery rejects. A failed or interrupted wait counts as not run.
     */
    @NotNull
    private static SchemaRefreshOutcome awaitSchemaRefresh(@NotNull Project project,
                                                           @NotNull CompiledGraph graph,
                                                           @NotNull Set<String> failedFiles) {
        try {
            return DataformTableSchemaService.getInstance(project).refresh(graph, true, failedFiles).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return SchemaRefreshOutcome.NOT_RUN;
        } catch (ExecutionException e) {
            LOG.warn("Schema refresh after the build failed", e);
            return SchemaRefreshOutcome.NOT_RUN;
        }
    }

    private static void reportDryRunProblem(BuildViewManager buildViewManager, DataformBuildContext context,
                                            @NotNull DryRunBuildProblem problem) {
        VirtualFile file = problem.file();
        FilePosition position = file == null ? null
                : new FilePosition(file.toNioPath(), problem.line(), problem.column());
        reportError(buildViewManager, context, "BigQuery: " + problem.message(),
                BuildErrorText.details(problem.actionFullName() + "\n" + problem.details()), position);
    }

    private static void reportError(BuildViewManager buildViewManager, DataformBuildContext context,
                                    String title, String description, @Nullable FilePosition position) {
        buildViewManager.onEvent(context, errorEvent(context, title, description, position));
    }

    /**
     * An error of the build. One with a file position is listed under a node of its file, which
     * opens the file at that position; one without stays directly under the build.
     */
    @NotNull
    static MessageEvent errorEvent(@NotNull Object buildId, @NotNull String title, @NotNull String description,
                                   @Nullable FilePosition position) {
        var event = MessageEvent.builder(title, MessageEvent.Kind.ERROR)
                .withDescription(description)
                .withParentId(buildId)
                .withGroup(TASK_NAME);
        if (position != null) event = event.withFilePosition(position);
        return event.build();
    }

    /**
     * Reports a run of the CLI that compiled nothing: one error explaining it, one placed on the
     * setting of {@code workflow_settings.yaml} it is about when there is one, and the output of the
     * CLI in the console of the build node.
     */
    private static void reportCliFailure(@NotNull Project project, @NotNull BuildViewManager buildViewManager,
                                         @NotNull DataformBuildContext context, @NotNull CliRun run,
                                         @NotNull CliFailure failure, @Nullable CompiledGraph graph) {
        boolean previousGraph = graph != null && compiledActions(graph) > 0;
        reportError(buildViewManager, context, failure.title(),
                CliFailureExplainer.describe(failure, run, previousGraph), null);
        String key = failure.settingsKey();
        if (key != null) {
            SettingPlacement setting = ReadAction.nonBlocking(() -> settingPlacement(project, key)).executeSynchronously();
            if (setting != null) {
                reportError(buildViewManager, context, failure.title(), setting.description(failure), setting.position());
            }
        }
        String stderr = run.stderr().isBlank() || run.stderr().endsWith("\n") ? run.stderr() : run.stderr() + "\n";
        buildViewManager.onEvent(context, output(context, BuildSummaryText.header(run) + stderr + "\n"
                + BuildSummaryText.processEnd(run)));
        finishBuild(buildViewManager, context, false, failure.title());
        showNotification(project, failure.title(), "See Build window", NotificationType.ERROR);
    }

    @Nullable
    private static SettingPlacement settingPlacement(@NotNull Project project, @NotNull String key) {
        VirtualFile file = DataformPaths.findInProject(project, DataformProjectLayout.WORKFLOW_SETTINGS_YAML);
        if (file == null) return null;
        Document document = FileDocumentManager.getInstance().getDocument(file);
        if (document == null) return null;
        String text = document.getText();
        int offset = CliFailureExplainer.keyOffset(text, key);
        if (offset < 0) return null;
        LineColumn at = StringUtil.offsetToLineColumn(text, offset);
        return new SettingPlacement(new FilePosition(file.toNioPath(), at.line, at.column),
                DataformProjectLayout.WORKFLOW_SETTINGS_YAML + ":" + (at.line + 1) + ":" + (at.column + 1) + "\n"
                        + CodeFrame.of(text, at.line, at.column, key.length()));
    }

    private record SettingPlacement(@NotNull FilePosition position, @NotNull String frame) {

        @NotNull
        String description(@NotNull CliFailure failure) {
            return BuildErrorText.details(String.join("\n", failure.cause()) + "\n" + String.join("\n", failure.fix()))
                    + "\n\n" + frame;
        }
    }

    /**
     * Text printed in the console of the build node.
     */
    @NotNull
    static OutputBuildEvent output(@NotNull Object buildId, @NotNull String text) {
        return OutputBuildEvent.builder(text)
                .withParentId(buildId)
                .withOutputType(ProcessOutputType.STDOUT)
                .build();
    }

    private static int compiledActions(@NotNull CompiledGraph graph) {
        return sizeOf(graph.getTables()) + sizeOf(graph.getAssertions()) + sizeOf(graph.getOperations());
    }

    private static int sizeOf(@Nullable List<?> list) {
        return list == null ? 0 : list.size();
    }

    /**
     * The position an error is listed at: where it is placed in its file, or the start of its file
     * when it is not placed there, as when it is thrown in an include.
     */
    @Nullable
    private static FilePosition filePosition(@NotNull Project project, @NotNull CompilationError error,
                                             @Nullable PlacedError placed) {
        FilePosition start = resolveFilePosition(project, error);
        if (start == null || placed == null || placed.thrownInPath() != null) return start;
        return new FilePosition(start.getPath(), placed.line(), placed.column());
    }

    @Nullable
    private static FilePosition resolveFilePosition(@NotNull Project project,
                                                    @NotNull CompilationError error) {
        String fileName = error.getFileName();
        if (fileName == null || fileName.isBlank()) return null;

        VirtualFile vf = DataformPaths.findInProject(project, fileName);
        if (vf == null) {
            File f = new File(DataformPaths.normalize(fileName));
            if (f.exists()) {
                return new FilePosition(f.toPath(), 0, 0);
            }
            return null;
        }

        return new FilePosition(vf.toNioPath(), 0, 0);
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
        DataformNotifications.create(title, content != null ? content : "", type)
                .notify(project);
    }
}