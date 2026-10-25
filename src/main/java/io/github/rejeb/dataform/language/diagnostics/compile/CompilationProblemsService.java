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
package io.github.rejeb.dataform.language.diagnostics.compile;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import io.github.rejeb.dataform.language.diagnostics.CompilationDiagnostic;
import io.github.rejeb.dataform.language.diagnostics.PlacedProblems;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Places the Dataform compilation errors of a SQLX or JavaScript file in the file as it reads now,
 * with a hint and fixes for each.
 */
public interface CompilationProblemsService extends ModificationTracker {

    /**
     * Returns the project-level instance.
     */
    static CompilationProblemsService getInstance(@NotNull Project project) {
        return project.getService(CompilationProblemsService.class);
    }

    /**
     * The compilation errors of a file: those reported for it, and, for a JavaScript file, those
     * raised in it while another file was compiled. The errors of a file the editor shows no
     * problems in, such as a SQL or YAML action, are all left unplaced.
     */
    @NotNull PlacedProblems diagnose(@NotNull PsiFile file);

    /**
     * Returns the diagnostics for the given file, computed from the cached compiled graph.
     */
    @NotNull List<CompilationDiagnostic> getDiagnostics(@NotNull VirtualFile file);

    /**
     * Returns the errors reported for other files whose stack runs through the given file, as an
     * error thrown in an include while an action was compiled.
     */
    @NotNull List<CompilationDiagnostic> getDiagnosticsRaisedIn(@NotNull VirtualFile file);

    /**
     * Drops every cached diagnostic and counts as a change. Called after a recompile.
     */
    void invalidate();
}
