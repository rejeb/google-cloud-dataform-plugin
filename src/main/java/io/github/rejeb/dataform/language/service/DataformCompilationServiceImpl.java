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
import com.google.gson.GsonBuilder;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.util.ExecUtil;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vfs.VirtualFile;
import io.github.rejeb.dataform.language.compilation.model.CompilationError;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.compilation.model.GraphErrors;
import io.github.rejeb.dataform.setup.DataformInterpreterManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

@State(
        name = "DataformCompilationService",
        storages = @Storage(value = StoragePathMacros.CACHE_FILE)
)
public final class DataformCompilationServiceImpl
        implements DataformCompilationService {

    private static final Logger LOG = Logger.getInstance(DataformCompilationServiceImpl.class);
    private static final Gson GSON = new GsonBuilder().create();

    private final Project project;

    private volatile CompiledGraph compiledGraph;


    private State currentState = new State();

    public DataformCompilationServiceImpl(Project project) {
        this.project = project;
    }

    @Override
    public @Nullable State getState() {
        return currentState;
    }

    @Override
    public void loadState(@NotNull State state) {
        this.currentState = state;
        if (state.compiledGraphJson != null && !state.compiledGraphJson.isBlank()) {
            try {
                this.compiledGraph = GSON.fromJson(state.compiledGraphJson, CompiledGraph.class);
                LOG.info("Restored compiled graph from persistent state");
            } catch (Exception e) {
                LOG.warn("Failed to deserialize compiled graph from state: " + e.getMessage());
                this.compiledGraph = null;
                this.currentState = new State(); // reset corrupted state
            }
        }
    }

    @Override
    public CompiledGraph compile() {
        try {
            Optional<GeneralCommandLine> cmd = project.getService(DataformInterpreterManager.class)
                    .buildDataformCompileCommand();
            if (cmd.isPresent()) {
                ProcessOutput output = ExecUtil.execAndGetOutput(cmd.get(), 300000);
                String compilationResult = output.getStdout();

                if (compilationResult.isBlank()) {
                    CompilationError compilationError = new CompilationError(output.getStderr());
                    GraphErrors graphErrors = new GraphErrors();
                    graphErrors.setCompilationErrors(List.of(compilationError));
                    this.compiledGraph = new CompiledGraph();
                    this.compiledGraph.setGraphErrors(graphErrors);
                } else {
                    this.compiledGraph = GSON.fromJson(compilationResult, CompiledGraph.class);
                    currentState.compiledGraphJson = GSON.toJson(this.compiledGraph);
                    currentState.lastCompileTimestamp = System.currentTimeMillis();
                }

                return this.compiledGraph;
            }
        } catch (ExecutionException e) {
            LOG.error("Error during compilation", e);
        }
        return null;
    }

    @Override
    public CompiledGraph runIfFilesChanged() {
        if (project.isDisposed()) {
            return null;
        }
        if (compiledGraph != null
                && currentState.lastCompileTimestamp > 0
                && !hasSourcesChangedSince(currentState.lastCompileTimestamp)) {
            LOG.info("Sources unchanged, using cached compiled graph");
            return compiledGraph;
        }
        return compile();
    }

    @Override
    public CompiledGraph getCompiledGraph() {
        return compiledGraph;
    }

    @Override
    public void dispose() {
        compiledGraph = null;
    }

    private boolean hasSourcesChangedSince(long referenceTime) {
        VirtualFile projectDir = ProjectUtil.guessProjectDir(project);
        if (projectDir == null) return true;

        for (String dirName : List.of("definitions", "includes")) {
            VirtualFile dir = projectDir.findChild(dirName);
            if (dir != null && isModifiedAfter(dir, referenceTime)) return true;
        }

        for (String fileName : List.of("dataform.json", "workflow_settings.yaml")) {
            VirtualFile file = projectDir.findChild(fileName);
            if (file != null && file.getTimeStamp() > referenceTime) return true;
        }

        return false;
    }

    private boolean isModifiedAfter(@NotNull VirtualFile dir, long referenceTime) {
        for (VirtualFile child : dir.getChildren()) {
            if (child.isDirectory()) {
                if (isModifiedAfter(child, referenceTime)) return true;
            } else {
                String ext = child.getExtension();
                if ("sqlx".equals(ext) || "js".equals(ext) || "yaml".equals(ext)) {
                    if (child.getTimeStamp() > referenceTime) return true;
                }
            }
        }
        return false;
    }
}
