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
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.AppExecutorUtil;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.WorkflowCreationResult;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.WorkflowInvocationProgress;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.WorkflowInvocationState;
import io.github.rejeb.dataform.language.gcp.execution.workflow.runconfig.ui.WorkflowExecutionConsole;
import io.github.rejeb.dataform.language.gcp.service.DataformGcpEvent;
import io.github.rejeb.dataform.language.gcp.service.DataformGcpService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class DataformWorkflowRunProfileState
        implements com.intellij.execution.configurations.RunProfileState {

    private static final Logger LOG = Logger.getInstance(DataformWorkflowRunProfileState.class);
    private static final int POLL_INTERVAL_MS = 500;

    private final ExecutionEnvironment environment;
    private final DataformWorkflowRunConfiguration configuration;

    public DataformWorkflowRunProfileState(
            @NotNull ExecutionEnvironment environment,
            @NotNull DataformWorkflowRunConfiguration configuration) {
        this.environment = environment;
        this.configuration = configuration;
    }

    @Nullable
    @Override
    public ExecutionResult execute(
            @NotNull Executor executor,
            @NotNull ProgramRunner<?> runner) throws ExecutionException {

        Project project = environment.getProject();
        DataformProcessHandler processHandler = new DataformProcessHandler();
        processHandler.startNotify();

        WorkflowExecutionConsole console =
                new WorkflowExecutionConsole("Instantiating Dataform Workflow", project);

        ProgressManager.getInstance().run(buildTask(project, processHandler, console));

        return new DefaultExecutionResult(console, processHandler);
    }

    @NotNull
    private Task.Backgroundable buildTask(@NotNull Project project,
                                          @NotNull DataformProcessHandler processHandler,
                                          @NotNull WorkflowExecutionConsole console) {
        return new Task.Backgroundable(project, "Running Dataform workflow…", true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    runWorkflow(project, indicator, console);
                } catch (Exception e) {
                    LOG.warn("Dataform workflow execution failed", e);
                    processHandler.notifyProcessTerminated(1);
                } finally {
                    processHandler.notifyProcessTerminated(0);
                }
            }
        };
    }

    private void runWorkflow(@NotNull Project project,
                             @NotNull ProgressIndicator indicator,
                             @NotNull WorkflowExecutionConsole console) throws Exception {
        DataformGcpService service = DataformGcpService.getInstance(project);

        publishAndDisplay(project, console, uploadingProgress());
        service.pushCode(configuration.getWorkspaceId());

        publishAndDisplay(project, console, startingProgress());
        WorkflowCreationResult workflowRun = service.createWorkflowRun(configuration.toWorkflowRunRequest());

        notifyRunStarted(project, workflowRun.invocationName());
        pollUntilTerminal(project, indicator, console, service, workflowRun);
    }

    private void pollUntilTerminal(@NotNull Project project,
                                   @NotNull ProgressIndicator indicator,
                                   @NotNull WorkflowExecutionConsole console,
                                   @NotNull DataformGcpService service,
                                   @NotNull WorkflowCreationResult workflowRun) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        ScheduledFuture<?> poller = schedulePoller(project, indicator, console, service, workflowRun, latch);
        try {
            latch.await();
        } finally {
            poller.cancel(false);
        }
        if (indicator.isCanceled()) {
            service.cancelWorkflowRun(workflowRun.invocationName());
        }
    }

    @NotNull
    private ScheduledFuture<?> schedulePoller(@NotNull Project project,
                                              @NotNull ProgressIndicator indicator,
                                              @NotNull WorkflowExecutionConsole console,
                                              @NotNull DataformGcpService service,
                                              @NotNull WorkflowCreationResult workflowRun,
                                              @NotNull CountDownLatch latch) {
        return AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(
                () -> pollOnce(project, indicator, console, service, workflowRun, latch),
                POLL_INTERVAL_MS, POLL_INTERVAL_MS, TimeUnit.MILLISECONDS
        );
    }

    private void pollOnce(@NotNull Project project,
                          @NotNull ProgressIndicator indicator,
                          @NotNull WorkflowExecutionConsole console,
                          @NotNull DataformGcpService service,
                          @NotNull WorkflowCreationResult workflowRun,
                          @NotNull CountDownLatch latch) {
        if (indicator.isCanceled()) {
            latch.countDown();
            return;
        }
        try {
            WorkflowInvocationProgress progress = service.getWorkflowRunProgress(workflowRun);
            publishAndDisplay(project, console, progress);
            updateIndicatorText(indicator, progress, workflowRun.invocationName());
            if (progress.isTerminal()) latch.countDown();
        } catch (Exception e) {
            LOG.warn("Error polling workflow progress", e);
            latch.countDown();
        }
    }

    private void publishAndDisplay(@NotNull Project project,
                                   @NotNull WorkflowExecutionConsole console,
                                   @NotNull WorkflowInvocationProgress progress) {
        project.getMessageBus()
                .syncPublisher(DataformGcpEvent.TOPIC)
                .onWorkflowInvocationProgress(progress);
        SwingUtilities.invokeLater(() -> console.updateProgress(progress));
    }

    private void notifyRunStarted(@NotNull Project project, @NotNull String runName) {
        project.getMessageBus()
                .syncPublisher(DataformGcpEvent.TOPIC)
                .onWorkflowRunStarted(runName);
    }

    private void updateIndicatorText(@NotNull ProgressIndicator indicator,
                                     @NotNull WorkflowInvocationProgress progress,
                                     @NotNull String runName) {
        indicator.setText("Dataform: " + progress.state().name().toLowerCase() + " — " + runName);
    }

    @NotNull
    private WorkflowInvocationProgress uploadingProgress() {
        return new WorkflowInvocationProgress(
                "Uploading files to workspace " + configuration.getWorkspaceId(),
                WorkflowInvocationState.RUNNING,
                List.of(),
                null
        );
    }

    @NotNull
    private WorkflowInvocationProgress startingProgress() {
        return new WorkflowInvocationProgress(
                "Starting Workflow execution " + configuration.getWorkspaceId(),
                WorkflowInvocationState.RUNNING,
                List.of(),
                null
        );
    }
}