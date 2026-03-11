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
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vfs.VirtualFile;
import io.github.rejeb.dataform.language.compilation.model.CompilationError;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.compilation.model.GraphErrors;
import io.github.rejeb.dataform.setup.DataformInterpreterManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public final class DataformCompilationServiceImpl implements DataformCompilationService {
    private static final Logger LOG = Logger.getInstance(DataformCompilationServiceImpl.class);
    private final Project project;
    private volatile CompiledGraph compiledGraph;

    public DataformCompilationServiceImpl(Project project) {
        this.project = project;
    }


    public CompiledGraph compile() {
        try {
            Optional<GeneralCommandLine> cmd = project.getService(DataformInterpreterManager.class)
                    .buildDataformCompileCommand();
            if (cmd.isPresent()) {
                ProcessOutput output = ExecUtil.execAndGetOutput(cmd.get(), 300000);
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                String compilationResult = output.getStdout();
                if (compilationResult.isBlank()) {
                    CompilationError compilationError = new CompilationError(output.getStderr());
                    GraphErrors graphErrors = new GraphErrors();
                    graphErrors.setCompilationErrors(List.of(compilationError));
                    this.compiledGraph = new CompiledGraph();
                    this.compiledGraph.setGraphErrors(graphErrors);
                } else {
                    this.compiledGraph = gson.fromJson(compilationResult, CompiledGraph.class);
                }
                getCompileResultPath().ifPresent(this::saveToFile);
                return this.compiledGraph;
            }
        } catch (ExecutionException e) {
            LOG.error("Error during Indexing", e);
        }
        return null;
    }

    private void saveToFile(Path compileResultPath) {
        try {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            Files.writeString(compileResultPath, gson.toJson(this.compiledGraph));
        } catch (IOException e) {
            LOG.error("Error during save to file", e);
        }
    }

    private Optional<Path> getCompileResultPath() {

        return Optional
                .ofNullable(ProjectUtil.guessProjectDir(project))
                .map(basePath -> {
                    try {
                        Path buildDir = Path.of(basePath.getPath(), ".build");
                        Files.createDirectories(buildDir);
                        return buildDir.resolve("dataform-compile.json");
                    } catch (IOException e) {
                        LOG.error("Error during resolving compile result path", e);
                    }
                    return null;
                });

    }

    public CompiledGraph runIfFilesChanged() {
        if (project.isDisposed()) {
            return null;
        }
        try {
            Optional<Path> compilationResultPath = getCompileResultPath();

            if (compilationResultPath.isPresent() &&
                    Files.exists(compilationResultPath.get()) &&
                    compilationResultPath.stream().noneMatch(this::hasSourcesChangedSince)) {
                this.compiledGraph = new Gson().fromJson(Files.readString(compilationResultPath.get()), CompiledGraph.class);
            } else {
                return compile();
            }
        } catch (IOException e) {
            LOG.error("Error during resolving compiled graph", e);
        }
        return compiledGraph;
    }

    public CompiledGraph getCompiledGraph() {
        return compiledGraph;
    }

    @Override
    public void dispose() {
        compiledGraph = null;
    }

    private boolean hasSourcesChangedSince(Path referenceFile) {
        try {
        long lastCompileTime = Files.getLastModifiedTime(referenceFile).toMillis();

        VirtualFile projectDir = ProjectUtil.guessProjectDir(project);
        if (projectDir == null) return true;

        List<String> sourceDirs = List.of("definitions", "includes");

        for (String dirName : sourceDirs) {
            VirtualFile dir = projectDir.findChild(dirName);
            if (dir == null) continue;
            if (isModifiedAfter(dir, lastCompileTime)) return true;
        }

        List<String> configFiles = List.of("dataform.json", "workflow_settings.yaml");
        for (String fileName : configFiles) {
            VirtualFile file = projectDir.findChild(fileName);
            if (file != null && file.getTimeStamp() > lastCompileTime) return true;
        }
        } catch (IOException e) {
            LOG.error("Error checking for changes", e);
        }

        return false;

    }

    private boolean isModifiedAfter(VirtualFile dir, long referenceTime) {
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

