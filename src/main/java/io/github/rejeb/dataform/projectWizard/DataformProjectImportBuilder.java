/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.rejeb.dataform.projectWizard;

import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.ModifiableArtifactModel;
import com.intellij.projectImport.ProjectImportBuilder;
import io.github.rejeb.dataform.language.DataformIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public class DataformProjectImportBuilder extends ProjectImportBuilder<VirtualFile> {

    @Override
    public @NotNull String getName() {
        return "Dataform";
    }

    @Override
    public Icon getIcon() {
        return DataformIcons.FILE;
    }

    @Override
    public List<VirtualFile> getList() { return List.of(); }

    @Override
    public boolean isMarked(VirtualFile element) { return false; }

    @Override
    public void setList(List<VirtualFile> list) {}

    @Override
    public void setOpenProjectSettingsAfter(boolean on) {}

    @Override
    public @Nullable List<Module> commit(Project project, ModifiableModuleModel model,
                                         ModulesProvider modulesProvider,
                                         ModifiableArtifactModel artifactModel) {
        Module module = model.newModule(
                project.getBasePath() + "/" + project.getName() + ".iml",
                DataformModuleType.ID
        );
        ModifiableRootModel rootModel = ModuleRootManager.getInstance(module).getModifiableModel();
        VirtualFile baseDir = ProjectUtil.guessProjectDir(project);
        if (baseDir != null) {
            rootModel.addContentEntry(baseDir);
        }
        rootModel.commit();
        return List.of(module);
    }
}
