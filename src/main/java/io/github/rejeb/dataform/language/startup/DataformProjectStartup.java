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
package io.github.rejeb.dataform.language.startup;

import com.intellij.facet.FacetManager;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.startup.ProjectActivity;
import com.intellij.openapi.vfs.VirtualFile;
import io.github.rejeb.dataform.language.gcp.service.DataformGcpService;
import io.github.rejeb.dataform.language.gcp.settings.GcpRepositorySettings;
import io.github.rejeb.dataform.language.schema.dts.DataformDtsGenerator;
import io.github.rejeb.dataform.language.projectWizard.DataformFacet;
import io.github.rejeb.dataform.language.projectWizard.DataformFacetType;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class DataformProjectStartup implements ProjectActivity {

    private static final Logger LOG = Logger.getInstance(DataformProjectStartup.class);

    @Nullable
    @Override
    public Object execute(@NotNull Project project,
                          @NotNull Continuation<? super Unit> continuation) {

        VirtualFile baseDir = ProjectUtil.guessProjectDir(project);
        if (baseDir == null) return null;

        boolean isDataformProject =
                baseDir.findChild("dataform.json") != null ||
                        baseDir.findChild("workflow_settings.yaml") != null;

        if (!isDataformProject) return null;

        WriteAction.runAndWait(() -> {
            project.getService(DataformDtsGenerator.class).generateDts();
            ModuleManager moduleManager = ModuleManager.getInstance(project);

            Module[] modules = moduleManager.getModules();

            for (Module module : modules) {
                FacetManager facetManager = FacetManager.getInstance(module);

                if (facetManager.getFacetByType(DataformFacetType.ID) == null) {
                    ModifiableFacetModel model = facetManager.createModifiableModel();
                    DataformFacet facet = facetManager.createFacet(
                            DataformFacetType.INSTANCE, "Dataform", null);
                    model.addFacet(facet);
                    model.commit();
                    LOG.info("Dataform facet added to module: " + module.getName());
                }
            }
            try {
                VirtualFile[] roots = ProjectRootManager.getInstance(project).getContentRoots();
                if (roots.length == 0) return;
                VirtualFile contentRoot = roots[0];
                ensureGitignoreContainsDataform(contentRoot);
            } catch (IOException e) {
            }
        });

        if (GcpRepositorySettings.getInstance(project).getConfig() == null) {
            return null;
        }
        if (GcpRepositorySettings.getInstance(project).getConfig() != null) {
            List<String> cached = DataformGcpService.getInstance(project).getCachedFiles();
            if (cached.isEmpty()) {
                DataformGcpService.getInstance(project).refreshFilesAsync(null, files ->
                        LOG.info("Dataform GCP file cache loaded: " + files.size() + " files.")
                );
            } else {
                LOG.info("Dataform GCP file cache restored from disk: " + cached.size() + " files.");
            }

        }

        return null;
    }

    private static void ensureGitignoreContainsDataform(
            @NotNull VirtualFile contentRoot
    ) throws IOException {
        VirtualFile gitignore = contentRoot.findChild(".gitignore");

        if (gitignore == null) {
            gitignore = contentRoot.createChildData(null, ".gitignore");
            gitignore.setBinaryContent(".dataform/\n".getBytes(StandardCharsets.UTF_8));
            return;
        }

        String content = new String(gitignore.contentsToByteArray(), StandardCharsets.UTF_8);
        // Vérifier si .dataform/ est déjà présent (avec ou sans slash)
        boolean alreadyPresent = java.util.Arrays.stream(content.split("\\R"))
                .map(String::trim)
                .anyMatch(line -> line.equals(".dataform/") || line.equals(".dataform"));

        if (!alreadyPresent) {
            String updated = content.endsWith("\n")
                    ? content + ".dataform/\n"
                    : content + "\n.dataform/\n";
            gitignore.setBinaryContent(updated.getBytes(StandardCharsets.UTF_8));
        }
    }
}
