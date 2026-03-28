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

import com.intellij.openapi.project.Project;
import com.intellij.task.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import java.util.concurrent.Future;

public final class DataformBuildTaskRunner extends ProjectTaskRunner {

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
                promise.setResult(TaskRunnerResults.ABORTED);
            } else if (buildResult.succeeded) {
                promise.setResult(TaskRunnerResults.SUCCESS);
            } else {
                promise.setResult(TaskRunnerResults.FAILURE);
            }
        } catch (Exception e) {
            promise.setResult(TaskRunnerResults.FAILURE);
        }

        return promise;
    }

    @Override
    public boolean canRun(@NotNull ProjectTask projectTask) {
        return projectTask instanceof ModuleBuildTask;
    }
}