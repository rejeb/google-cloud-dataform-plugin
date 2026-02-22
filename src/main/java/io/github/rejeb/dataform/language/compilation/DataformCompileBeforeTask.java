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
package io.github.rejeb.dataform.language.compilation;

import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileTask;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vfs.VirtualFile;
import io.github.rejeb.dataform.language.compilation.model.CompilationError;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.service.DataformCompilationService;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class DataformCompileBeforeTask implements CompileTask {
    private static final Logger LOG = Logger.getInstance(DataformCompileBeforeTask.class);

    @Override
    public boolean execute(@NotNull CompileContext context) {
        if (!isDataformProject(context.getProject())) {
            return true;
        }
        DataformCompilationService compilationService = context.getProject().getService(DataformCompilationService.class);
        if (context.isAutomake()) {
            return true;
        }

        boolean hasErrors = false;
        try {
            context.addMessage(CompilerMessageCategory.INFORMATION, "Running dataform compile", null, -1, -1);

            CompiledGraph compiledGraph = compilationService.compile();
            hasErrors = compiledGraph == null ||
                        compiledGraph.getGraphErrors() != null &&
                        !compiledGraph.getGraphErrors().getCompilationErrors().isEmpty();

            if (hasErrors) {
                printErrors(context, compiledGraph.getGraphErrors().getCompilationErrors());
            } else {
                context.addMessage(CompilerMessageCategory.INFORMATION, "Dataform compile succeeded", null, -1, -1);
            }
        } catch (Exception e) {
            LOG.error("Error during compile", e);
            context.addMessage(CompilerMessageCategory.ERROR,
                    "Failed to run dataform compile: " + e.getMessage(), null, -1, -1);
        }

        return !hasErrors;
    }

    private boolean isDataformProject(@NotNull Project project) {
        VirtualFile baseDir = ProjectUtil.guessProjectDir(project);
        if (baseDir == null) {
            return false;
        }

        return baseDir.findChild("dataform.json") != null
                || baseDir.findChild("workflow_settings.yaml") != null;

    }

    private void printErrors(@NotNull CompileContext context, List<CompilationError> compilationErrors) {
        VirtualFile basePath = ProjectUtil.guessProjectDir(context.getProject());
        for (CompilationError error : compilationErrors) {
            VirtualFile vf = basePath.findFileByRelativePath(error.getFileName());
            String url = vf != null ? vf.getUrl() : null;
            String fullMessage = error.getMessage();
            if (error.getStack() != null && !error.getStack().isBlank()) {
                fullMessage += "\n" + error.getStack();
            }
            context.addMessage(
                    CompilerMessageCategory.ERROR,
                    fullMessage,
                    url,
                    -1, -1
            );
        }
    }

}
