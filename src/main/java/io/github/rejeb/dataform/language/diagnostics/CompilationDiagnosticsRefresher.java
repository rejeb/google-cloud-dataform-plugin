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
package io.github.rejeb.dataform.language.diagnostics;

import com.intellij.openapi.project.Project;
import io.github.rejeb.dataform.language.compilation.DataformCompilationEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Repaints the compilation errors once a compilation has changed the compiled graph, whichever
 * action ran it, so they appear, move or disappear without waiting for the next edit.
 */
public final class CompilationDiagnosticsRefresher implements DataformCompilationEvent {

    private final Project project;

    public CompilationDiagnosticsRefresher(@NotNull Project project) {
        this.project = project;
    }

    @Override
    public void onGraphChanged() {
        if (project.isDisposed()) return;
        CompilationDiagnosticService.getInstance(project).invalidate();
        ValidationProblemInlayManager.getInstance(project).refreshAll();
        DataformEditorRefresher.refresh(project);
    }
}
