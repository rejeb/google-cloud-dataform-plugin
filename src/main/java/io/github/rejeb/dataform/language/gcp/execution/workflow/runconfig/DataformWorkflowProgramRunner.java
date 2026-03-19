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
package io.github.rejeb.dataform.language.gcp.execution.workflow.runconfig;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.AsyncProgramRunner;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.RunContentBuilder;
import com.intellij.execution.ui.RunContentDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

public class DataformWorkflowProgramRunner
        extends AsyncProgramRunner<RunnerSettings> {

    public static final String RUNNER_ID = "DataformWorkflowRunner";

    @NotNull
    @Override
    public String getRunnerId() {
        return RUNNER_ID;
    }

    @Override
    public boolean canRun(@NotNull String executorId, @NotNull RunProfile profile) {
        return DefaultRunExecutor.EXECUTOR_ID.equals(executorId)
                && profile instanceof DataformWorkflowRunConfiguration;
    }

    @NotNull
    @Override
    protected Promise<RunContentDescriptor> execute(
            @NotNull ExecutionEnvironment environment,
            @NotNull RunProfileState state
    ) {
        try {
            ExecutionResult result = state.execute(
                    environment.getExecutor(), this);
            if (result == null) {
                return Promises.resolvedPromise(null);
            }
            RunContentDescriptor descriptor = new RunContentBuilder(result, environment)
                    .showRunContent(environment.getContentToReuse());
            return Promises.resolvedPromise(descriptor);
        } catch (ExecutionException e) {
            return Promises.rejectedPromise(e);
        }
    }
}
