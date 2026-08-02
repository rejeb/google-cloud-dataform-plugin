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

import com.intellij.openapi.vfs.AsyncFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import io.github.rejeb.dataform.language.util.DataformProjectLayout;
import io.github.rejeb.dataform.language.util.DataformProjects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Drops cached diagnostics and refreshes the editor when Dataform sources change on disk.
 */
public final class DiagnosticsRefreshListener implements AsyncFileListener {

    @Override
    public @Nullable ChangeApplier prepareChange(@NotNull List<? extends VFileEvent> events) {
        boolean relevant = events.stream()
                .anyMatch(event -> DataformProjectLayout.isDataformSource(event.getFile()));
        if (!relevant) {
            return null;
        }
        return new ChangeApplier() {
            @Override
            public void afterVfsChange() {
                DataformProjects.forEachOpen(project -> {
                    CompilationDiagnosticService.getInstance(project).invalidate();
                    ValidationProblemInlayManager.getInstance(project).refreshAll();
                    DataformEditorRefresher.refresh(project);
                });
            }
        };
    }
}
