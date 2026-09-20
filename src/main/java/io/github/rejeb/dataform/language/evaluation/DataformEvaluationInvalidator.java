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

import java.util.List;

/**
 * Notes, for every open project, that the files the expression values depend on have changed on
 * disk. Nothing is dropped: the values stay in front of whoever is editing, and the next pass of a
 * file computes them anew. A change to an ordinary file needs no note at all — its own PSI stamp
 * moves with it, and that is what a pass compares.
 */
public final class DataformEvaluationInvalidator implements AsyncFileListener {

    @Override
    public @Nullable ChangeApplier prepareChange(@NotNull List<? extends VFileEvent> events) {
        boolean environmentChanged = false;
        for (VFileEvent event : events) {
            VirtualFile file = event.getFile();
            if (file != null && affectsEnvironment(file)) {
                environmentChanged = true;
                break;
            }
        }
        if (!environmentChanged) {
            return null;
        }
        return new ChangeApplier() {
            @Override
            public void afterVfsChange() {
                for (Project project : ProjectManager.getInstance().getOpenProjects()) {
                    if (!project.isDisposed()) {
                        DataformExpressionEvaluationService.getInstance(project).markEnvironmentChanged();
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
