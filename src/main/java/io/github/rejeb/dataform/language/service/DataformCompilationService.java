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
package io.github.rejeb.dataform.language.service;

import com.google.gson.Gson;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.util.ExecUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.util.Alarm;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.setup.DataformInterpreterManager;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

@Service(Service.Level.PROJECT)
public final class DataformCompilationService implements Disposable {
    private static final Logger LOG = Logger.getInstance(DataformCompilationService.class);
    private static final int DEFAULT_DELAY_MS = 0;
    private final Project project;
    private final Alarm alarm;
    private volatile CompiledGraph compiledGraph;
    private static final int COMPILATION_DELAY_MS = 30000;

    public DataformCompilationService(Project project) {
        this.project = project;
        this.alarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, this);
        scheduleNextCompilation(DEFAULT_DELAY_MS);
    }

    private void scheduleNextCompilation(int delayMs) {
        if (!project.isDisposed() && !alarm.isDisposed()) {
            alarm.addRequest(this::runCompilation, delayMs);
        }
    }

    private void runCompilation() {
        if (project.isDisposed()) {
            return;
        }
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Indexing....", false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    Optional<GeneralCommandLine> cmd = project.getService(DataformInterpreterManager.class)
                            .getInterpreterCommand("compile", "--json");
                    if (cmd.isPresent()) {
                        ProcessOutput output = ExecUtil.execAndGetOutput(cmd.get(), 60000);
                        parseAndCacheResults(output.getStdout());
                    }
                } catch (ExecutionException e) {
                    LOG.error("Error during Indexing", e);
                } finally {
                    scheduleNextCompilation(COMPILATION_DELAY_MS);
                }
            }
        });
    }

    private void parseAndCacheResults(String jsonOutput) {
        Gson gson = new Gson();
        this.compiledGraph = gson.fromJson(jsonOutput, CompiledGraph.class);
    }

    public CompiledGraph getCompiledGraph() {
        return compiledGraph;
    }

    @Override
    public void dispose() {
        compiledGraph = null;
    }
}

