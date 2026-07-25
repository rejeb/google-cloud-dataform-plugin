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
package io.github.rejeb.dataform.language.evaluation;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.AsyncFileListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import io.github.rejeb.dataform.language.index.DataformJsFileIndex;
import io.github.rejeb.dataform.language.util.DataformProjectLayout;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Drops cached expression values when the files they were computed from change.
 */
public final class DataformEvaluationInvalidator implements AsyncFileListener {

    @Override
    public @Nullable ChangeApplier prepareChange(@NotNull List<? extends VFileEvent> events) {
        List<VirtualFile> changed = new ArrayList<>();
        boolean environmentChanged = false;
        for (VFileEvent event : events) {
            VirtualFile file = event.getFile();
            if (file == null) {
                continue;
            }
            if (affectsEnvironment(file)) {
                environmentChanged = true;
            } else {
                changed.add(file);
            }
        }
        if (!environmentChanged && changed.isEmpty()) {
            return null;
        }

        boolean invalidateAll = environmentChanged;
        return new ChangeApplier() {
            @Override
            public void afterVfsChange() {
                for (Project project : ProjectManager.getInstance().getOpenProjects()) {
                    if (project.isDisposed()) {
                        continue;
                    }
                    DataformExpressionEvaluationService service =
                            DataformExpressionEvaluationService.getInstance(project);
                    if (invalidateAll) {
                        service.invalidateAll();
                    } else {
                        changed.forEach(service::invalidate);
                    }
                }
            }
        };
    }

    private boolean affectsEnvironment(@NotNull VirtualFile file) {
        String name = file.getName();
        return DataformProjectLayout.WORKFLOW_SETTINGS_YAML.equals(name)
                || DataformProjectLayout.DATAFORM_JSON.equals(name)
                || DataformJsFileIndex.isDataformJsFile(file);
    }
}
