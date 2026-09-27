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
package io.github.rejeb.dataform.language.gcp.execution.unittest.runconfig;

import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessListener;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil;
import com.intellij.execution.testframework.ui.BaseTestsOutputConsoleView;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import io.github.rejeb.dataform.language.compilation.DataformCompilationService;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.gcp.execution.unittest.bigquery.BigQueryUnitTestQueryExecutor;
import io.github.rejeb.dataform.language.gcp.execution.unittest.engine.UnitTestCase;
import io.github.rejeb.dataform.language.gcp.execution.unittest.engine.UnitTestCases;
import io.github.rejeb.dataform.language.gcp.execution.unittest.engine.UnitTestRunner;
import io.github.rejeb.dataform.language.gcp.execution.workflow.runconfig.DataformProcessHandler;
import io.github.rejeb.dataform.language.gcp.settings.DataformRepositoryConfig;
import io.github.rejeb.dataform.language.gcp.settings.GcpRepositorySettings;
import io.github.rejeb.dataform.language.unittest.SqlxUnitTests;
import io.github.rejeb.dataform.language.util.DataformPaths;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class DataformTestRunProfileState implements RunProfileState {

    private final ExecutionEnvironment environment;
    private final DataformTestRunConfiguration configuration;

    public DataformTestRunProfileState(@NotNull ExecutionEnvironment environment,
                                       @NotNull DataformTestRunConfiguration configuration) {
        this.environment = environment;
        this.configuration = configuration;
    }

    @Override
    public @NotNull ExecutionResult execute(@NotNull Executor executor, @NotNull ProgramRunner<?> runner)
            throws ExecutionException {
        Project project = environment.getProject();
        DataformProcessHandler handler = new DataformProcessHandler();
        BaseTestsOutputConsoleView console = SMTestRunnerConnectionUtil.createAndAttachConsole(
                DataformTestConsoleProperties.FRAMEWORK_NAME, handler,
                new DataformTestConsoleProperties(configuration, executor));
        handler.startNotify();
        new Task.Backgroundable(project, "Dataform: running unit tests", true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                handler.addProcessListener(new ProcessListener() {
                    @Override
                    public void processWillTerminate(@NotNull ProcessEvent event, boolean willBeDestroyed) {
                        indicator.cancel();
                    }
                });
                int exitCode = 1;
                try {
                    exitCode = runTests(project, handler, indicator);
                } finally {
                    if (!handler.isProcessTerminated()) {
                        handler.notifyProcessTerminated(exitCode);
                    }
                }
            }
        }.queue();
        return new DefaultExecutionResult(console, handler);
    }

    private int runTests(@NotNull Project project, @NotNull DataformProcessHandler handler,
                         @NotNull ProgressIndicator indicator) {
        DataformRepositoryConfig config = GcpRepositorySettings.getInstance(project).getActiveConfig();
        if (config == null) {
            error(handler, "No GCP project configured: select a Dataform repository in the Dataform tool window.");
            return 1;
        }
        indicator.setText("Compiling Dataform project...");
        CompiledGraph graph = DataformCompilationService.getInstance(project).compile(false);
        if (graph == null) {
            error(handler, "Dataform compilation failed.");
            return 1;
        }
        List<UnitTestCase> cases = UnitTestCases.select(graph, configuration.getScope(), configuration.getTargetPath(),
                fileName -> isUnitTestFile(project, fileName));
        if (cases.isEmpty()) {
            error(handler, "No unit tests found.");
            return 1;
        }
        indicator.setText("Running " + cases.size() + " unit tests...");
        ServiceMessageUnitTestListener listener = new ServiceMessageUnitTestListener(
                text -> handler.notifyTextAvailable(text, ProcessOutputTypes.STDOUT));
        new UnitTestRunner(new BigQueryUnitTestQueryExecutor(project, config.projectId()), listener)
                .run(cases, indicator);
        return listener.failureCount() == 0 ? 0 : 1;
    }

    private static boolean isUnitTestFile(@NotNull Project project, @NotNull String projectRelativePath) {
        return ReadAction.computeBlocking(() -> {
            VirtualFile file = DataformPaths.findInProject(project, projectRelativePath);
            PsiFile psiFile = file == null || !file.isValid() ? null : PsiManager.getInstance(project).findFile(file);
            return psiFile != null && SqlxUnitTests.isUnitTestFile(psiFile);
        });
    }

    private static void error(@NotNull DataformProcessHandler handler, @NotNull String message) {
        handler.notifyTextAvailable(message + "\n", ProcessOutputTypes.STDERR);
    }
}
