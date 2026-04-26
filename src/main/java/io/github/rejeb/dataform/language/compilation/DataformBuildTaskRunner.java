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

import com.intellij.openapi.project.Project;
import com.intellij.task.ModuleBuildTask;
import com.intellij.task.ProjectTask;
import com.intellij.task.ProjectTaskContext;
import com.intellij.task.ProjectTaskRunner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import java.util.concurrent.Future;

import static io.github.rejeb.dataform.language.util.Utils.isDataformProject;

public final class DataformBuildTaskRunner extends ProjectTaskRunner {
    private static final TaskResult SUCCESS = new TaskResult(false, false);
    private static final TaskResult FAILURE = new TaskResult(false, true);
    private static final TaskResult ABORTED = new TaskResult(true, false);

    @Override
    public @NotNull Promise<Result> run(@NotNull Project project,
                                        @NotNull ProjectTaskContext context,
                                        ProjectTask @NotNull ... tasks) {
        if (project.isDisposed()) {
            return Promises.rejectedPromise("Project is disposed");
        }

        AsyncPromise<Result> promise = new AsyncPromise<>();

        try {
            Future<DataformBuildResult> future = DataformBuildManager.build(project);
            DataformBuildResult buildResult = future.get();

            if (buildResult.canceled) {
                promise.setResult(ABORTED);
            } else if (buildResult.succeeded) {
                promise.setResult(SUCCESS);
            } else {
                promise.setResult(FAILURE);
            }
        } catch (Exception e) {
            promise.setResult(FAILURE);
        }

        return promise;
    }

    @Override
    public boolean canRun(@NotNull ProjectTask projectTask) {
        if (projectTask instanceof ModuleBuildTask moduleBuildTask) {
            return isDataformProject(moduleBuildTask.getModule().getProject());
        }
        return false;
    }

    private record TaskResult(boolean myAborted, boolean myErrors) implements Result {
        @Override
        public boolean isAborted() {
            return myAborted;
        }

        @Override
        public boolean hasErrors() {
            return myErrors;
        }
    }
}