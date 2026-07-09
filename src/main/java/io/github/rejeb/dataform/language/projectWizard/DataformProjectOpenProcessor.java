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
package io.github.rejeb.dataform.language.projectWizard;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.ProjectOpenProcessor;
import io.github.rejeb.dataform.language.DataformIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public final class DataformProjectOpenProcessor extends ProjectOpenProcessor {

    @Override
    public @NotNull String getName() {
        return "Dataform";
    }

    @Override
    public Icon getIcon() {
        return DataformIcons.FILE;
    }

    @Override
    public boolean canOpenProject(@NotNull VirtualFile file) {
        if (file.isDirectory()) {
            return file.findChild("workflow_settings.yaml") != null
                    || file.findChild("dataform.json") != null;
        }
        return false;
    }

    @Override
    public @Nullable Object openProjectAsync(@NotNull VirtualFile virtualFile,
                                             @Nullable Project projectToClose,
                                             boolean forceOpenInNewFrame,
                                             @NotNull kotlin.coroutines.Continuation<? super Project> $completion) {
        OpenProjectTask task = OpenProjectTask.build()
                .withProjectToClose(projectToClose)
                .withForceOpenInNewFrame(forceOpenInNewFrame);
        return ProjectManagerEx.getInstanceEx().openProject(virtualFile.toNioPath(), task);
    }

}