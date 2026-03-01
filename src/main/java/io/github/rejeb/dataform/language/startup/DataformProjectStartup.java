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
package io.github.rejeb.dataform.language.startup;

import com.intellij.facet.FacetManager;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.startup.ProjectActivity;
import com.intellij.openapi.vfs.VirtualFile;
import io.github.rejeb.dataform.projectWizard.DataformFacet;
import io.github.rejeb.dataform.projectWizard.DataformFacetType;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DataformProjectStartup implements ProjectActivity {

    @Nullable
    @Override
    public Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
        VirtualFile baseDir = ProjectUtil.guessProjectDir(project);
        if (baseDir == null) return null;

        boolean isDataformProject =
                baseDir.findChild("dataform.json") != null ||
                        baseDir.findChild("workflow_settings.yaml") != null;

        if (!isDataformProject) return null;

        // Ajoute le facet Dataform sur chaque module qui n'en a pas encore
        WriteAction.runAndWait(() -> {
            for (Module module : ModuleManager.getInstance(project).getModules()) {
                FacetManager facetManager = FacetManager.getInstance(module);

                if (facetManager.getFacetByType(DataformFacetType.ID) == null) {
                    ModifiableFacetModel model = facetManager.createModifiableModel();
                    DataformFacet facet = FacetManager.getInstance(module)
                            .createFacet(
                                    DataformFacetType.INSTANCE,
                                    "Dataform",
                                    null
                            );
                    model.addFacet(facet);
                    model.commit();
                }
            }
        });
        return null;
    }

}