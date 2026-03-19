/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.rejeb.dataform.language.gcp.execution.workflow.runconfig;

import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.WorkflowInvocationProgress;
import io.github.rejeb.dataform.language.gcp.service.DataformGcpEvent;
import io.github.rejeb.dataform.language.gcp.service.DataformGcpService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public class DataformWorkflowRunProfileState implements com.intellij.execution.configurations.RunProfileState {

    private static final Logger LOG = Logger.getInstance(DataformWorkflowRunProfileState.class);
    private static final int POLL_INTERVAL_MS = 3_000;

    private final ExecutionEnvironment environment;
    private final DataformWorkflowRunConfiguration configuration;

    public DataformWorkflowRunProfileState(
            @NotNull ExecutionEnvironment environment,
            @NotNull DataformWorkflowRunConfiguration configuration
    ) {
        this.environment = environment;
        this.configuration = configuration;
    }

    @Nullable
    @Override
    public ExecutionResult execute(
            @NotNull Executor executor,
            @NotNull ProgramRunner<?> runner
    ) throws ExecutionException {
        Project project = environment.getProject();
        ConsoleView console = new ConsoleViewImpl(project, true);

        DataformProcessHandler processHandler = new DataformProcessHandler();

        console.attachToProcess(processHandler);
        processHandler.startNotify();

        ProgressManager.getInstance().run(new Task.Backgroundable(
                project, "Running Dataform workflow…", true) {

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                DataformGcpService service = DataformGcpService.getInstance(project);
                try {
                    String runName = service.createWorkflowRun(
                            configuration.toWorkflowRunRequest());

                    console.print("▶ Workflow execution started: " + runName + "\n",
                            ConsoleViewContentType.SYSTEM_OUTPUT);

                    project.getMessageBus()
                            .syncPublisher(DataformGcpEvent.TOPIC)
                            .onWorkflowRunStarted(runName);

                    // 2. Polling loop
                    while (!indicator.isCanceled()) {
                        Thread.sleep(POLL_INTERVAL_MS);

                        WorkflowInvocationProgress progress =
                                service.getWorkflowRunProgress(runName);

                        project.getMessageBus()
                                .syncPublisher(DataformGcpEvent.TOPIC)
                                .onWorkflowInvocationProgress(progress);

                        printProgress(console, progress);

                        if (progress.isTerminal()) break;

                        indicator.setText("Dataform: " + progress.state().name().toLowerCase()
                                + " — " + runName);
                    }

                    // 3. Cancel if user stopped
                    if (indicator.isCanceled()) {
                        service.cancelWorkflowRun(runName);
                        console.print("⛔ Workflow cancelled.\n",
                                ConsoleViewContentType.ERROR_OUTPUT);
                    }

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    LOG.warn("Dataform workflow execution failed", e);
                    console.print("✗ Error: " + e.getCause().getMessage() + "\n",
                            ConsoleViewContentType.ERROR_OUTPUT);
                    console.print(String.join("\n   ",Arrays.stream(e.getCause().getStackTrace()).map(StackTraceElement::toString).toList()),
                            ConsoleViewContentType.ERROR_OUTPUT);
                    processHandler.notifyProcessTerminated(1);
                } finally {
                    processHandler.notifyProcessTerminated(0);
                }
            }
        });

        return new DefaultExecutionResult(console, processHandler);
    }

    private static void printProgress(
            @NotNull ConsoleView console,
            @NotNull WorkflowInvocationProgress progress
    ) {
        progress.actions().forEach(action -> {
            String line = switch (action.state()) {
                case SUCCEEDED -> "  ✓ " + action.target();
                case FAILED -> "  ✗ " + action.target()
                        + (action.failureReason() != null ? " — " + action.failureReason() : "");
                case RUNNING -> "  ⟳ " + action.target();
                case SKIPPED -> "  ⊘ " + action.target();
                default -> null;
            };
            if (line != null) {
                ConsoleViewContentType type =
                        action.state() == io.github.rejeb.dataform.language.gcp
                                .execution.workflow.model.InvocationActionState.FAILED
                                ? ConsoleViewContentType.ERROR_OUTPUT
                                : ConsoleViewContentType.NORMAL_OUTPUT;
                console.print(line + "\n", type);
            }
        });
    }
}
