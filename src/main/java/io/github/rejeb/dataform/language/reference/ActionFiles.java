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
package io.github.rejeb.dataform.language.reference;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import io.github.rejeb.dataform.language.compilation.DataformCompilationService;
import io.github.rejeb.dataform.language.compilation.model.ActionReference;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.util.DataformPaths;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Finds the file an action is compiled from, as the references naming an action resolve it.
 */
public final class ActionFiles {

    private ActionFiles() {
    }

    /**
     * The file of the action a reference designates, matched as {@link CompiledGraph#fileOf} does.
     * Must be called inside a read action.
     *
     * @param project the project
     * @param action  the name, schema and database of the action
     * @return the file, or {@code null} before the first compilation or when no action matches
     */
    public static @Nullable PsiFile psiFileOf(@NotNull Project project, @NotNull ActionReference action) {
        CompiledGraph graph = DataformCompilationService.getInstance(project).getCompiledGraph();
        if (graph == null) {
            return null;
        }
        return graph.fileOf(action)
                .map(fileName -> DataformPaths.findPsiFileInProject(project, fileName))
                .orElse(null);
    }
}
