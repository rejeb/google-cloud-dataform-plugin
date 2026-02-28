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
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Alarm;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.setup.DataformInterpreterManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

@Service(Service.Level.PROJECT)
public final class DataformCompilationService implements Disposable {
    private static final Logger LOG = Logger.getInstance(DataformCompilationService.class);
    private static final String COMPILE_RESULT_PATH = ".build/dataform-compile.json";
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

    public CompiledGraph compile() {
        try {
            Optional<GeneralCommandLine> cmd = project.getService(DataformInterpreterManager.class)
                    .buildDataformCompileCommand();
            if (cmd.isPresent()) {
                ProcessOutput output = ExecUtil.execAndGetOutput(cmd.get(), 300000);
                Gson gson = new Gson();
                String compilationResult = output.getStdout();
                Path compileResultPath = getCompileResultPath();
                Files.writeString(compileResultPath, output.getStdout());
                this.compiledGraph = gson.fromJson(compilationResult, CompiledGraph.class);
                return this.compiledGraph;
            }
        } catch (ExecutionException | IOException e) {
            LOG.error("Error during Indexing", e);
        }
        return null;
    }

    private @Nullable Path getCompileResultPath() {
        try {
            String basePath = ProjectUtil.guessProjectDir(project).getPath();
            Path buildDir = Path.of(basePath, ".build");
            Files.createDirectories(buildDir);
            return buildDir.resolve("dataform-compile.json");
        } catch (IOException e) {
            LOG.error("Error during resolving compile result path", e);
        }
        return null;
    }

    private void runCompilation() {
        if (project.isDisposed()) {
            return;
        }
        try {
            Path compilationResultPath = getCompileResultPath();

            if (compilationResultPath != null && Files.exists(compilationResultPath) && !hasSourcesChangedSince(compilationResultPath)) {
                this.compiledGraph = new Gson().fromJson(Files.readString(compilationResultPath), CompiledGraph.class);
            } else {
                ProgressManager.getInstance().run(new Task.Backgroundable(project, "Indexing....", false) {
                    @Override
                    public void run(@NotNull ProgressIndicator indicator) {
                        compile();
                        scheduleNextCompilation(COMPILATION_DELAY_MS);
                    }
                });
            }
        } catch (IOException e) {
            LOG.error("Error during resolving compiled graph", e);
        }
    }

    public CompiledGraph getCompiledGraph() {
        return compiledGraph;
    }

    @Override
    public void dispose() {
        compiledGraph = null;
    }

    private boolean hasSourcesChangedSince(Path referenceFile) throws IOException {
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

