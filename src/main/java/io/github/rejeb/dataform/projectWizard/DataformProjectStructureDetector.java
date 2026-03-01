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
package io.github.rejeb.dataform.projectWizard;

import com.intellij.ide.util.importProject.ModuleDescriptor;
import com.intellij.ide.util.importProject.ProjectDescriptor;
import com.intellij.ide.util.projectWizard.importSources.DetectedProjectRoot;
import com.intellij.ide.util.projectWizard.importSources.DetectedSourceRoot;
import com.intellij.ide.util.projectWizard.importSources.ProjectFromSourcesBuilder;
import com.intellij.ide.util.projectWizard.importSources.ProjectStructureDetector;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;


public class DataformProjectStructureDetector extends ProjectStructureDetector {

    @NotNull
    @Override
    public DirectoryProcessingResult detectRoots(@NotNull File dir, File @NotNull [] children,
                                                 @NotNull File base,
                                                 @NotNull List<DetectedProjectRoot> result) {
        for (File child : children) {
            if (child.getName().equals("dataform.json") ||
                    child.getName().equals("workflow_settings.yaml")) {
                result.add(new DataformProjectRoot(dir));
                return DirectoryProcessingResult.SKIP_CHILDREN;
            }
        }
        return DirectoryProcessingResult.PROCESS_CHILDREN;
    }

    @Override
    public void setupProjectStructure(@NotNull Collection<DetectedProjectRoot> roots,
                                      @NotNull ProjectDescriptor projectDescriptor,
                                      @NotNull ProjectFromSourcesBuilder builder) {
        List<ModuleDescriptor> modules = new ArrayList<>();

        for (DetectedProjectRoot root : roots) {
            DetectedSourceRoot detectedSourceRoot = new DataformDetectedSourceRoot(root.getDirectory(), null);
            modules.add(new ModuleDescriptor(root.getDirectory(),
                    DataformModuleType.getInstance(),
                    detectedSourceRoot));
        }
        projectDescriptor.setModules(modules);

    }


}
