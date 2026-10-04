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
package io.github.rejeb.dataform.language.unittest.execution.runconfig;

import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.LazyRunConfigurationProducer;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import io.github.rejeb.dataform.language.compilation.DataformCompilationService;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.unittest.execution.engine.DataformTestScope;
import io.github.rejeb.dataform.language.unittest.execution.engine.UnitTestCases;
import io.github.rejeb.dataform.language.unittest.SqlxUnitTests;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DataformTestRunConfigurationProducer extends LazyRunConfigurationProducer<DataformTestRunConfiguration> {

    private record Target(@NotNull DataformTestScope scope, @NotNull String path, @NotNull String name) {
    }

    @Override
    public @NotNull ConfigurationFactory getConfigurationFactory() {
        return ConfigurationTypeUtil.findConfigurationType(DataformTestConfigurationType.class)
                .getConfigurationFactories()[0];
    }

    @Override
    protected boolean setupConfigurationFromContext(@NotNull DataformTestRunConfiguration configuration,
                                                    @NotNull ConfigurationContext context,
                                                    @NotNull Ref<PsiElement> sourceElement) {
        Target target = target(context);
        if (target == null) {
            return false;
        }
        configuration.setScope(target.scope());
        configuration.setTargetPath(target.path());
        configuration.setName(target.name());
        return true;
    }

    @Override
    public boolean isConfigurationFromContext(@NotNull DataformTestRunConfiguration configuration,
                                              @NotNull ConfigurationContext context) {
        Target target = target(context);
        return target != null && target.scope() == configuration.getScope()
                && target.path().equals(configuration.getTargetPath());
    }

    @Nullable
    private static Target target(@NotNull ConfigurationContext context) {
        Location<?> location = context.getLocation();
        if (location == null) {
            return null;
        }
        Project project = context.getProject();
        VirtualFile projectDir = ProjectUtil.guessProjectDir(project);
        if (projectDir == null) {
            return null;
        }
        PsiElement element = location.getPsiElement();
        if (element instanceof PsiDirectory directory) {
            String path = VfsUtilCore.getRelativePath(directory.getVirtualFile(), projectDir);
            if (path == null || !holdsCompiledTests(project, path)) {
                return null;
            }
            return path.isEmpty()
                    ? new Target(DataformTestScope.ALL, "", "All Dataform tests")
                    : new Target(DataformTestScope.DIRECTORY, path, "Tests in " + path);
        }
        PsiFile file = element.getContainingFile();
        VirtualFile virtualFile = file == null ? null : file.getVirtualFile();
        if (virtualFile == null || !SqlxUnitTests.isUnitTestFile(file)) {
            return null;
        }
        String path = VfsUtilCore.getRelativePath(virtualFile, projectDir);
        return path == null ? null : new Target(DataformTestScope.FILE, path, virtualFile.getNameWithoutExtension());
    }

    private static boolean holdsCompiledTests(@NotNull Project project, @NotNull String directory) {
        CompiledGraph graph = DataformCompilationService.getInstance(project).getCompiledGraph();
        return graph != null && graph.getTests().stream()
                .anyMatch(test -> UnitTestCases.selects(DataformTestScope.DIRECTORY, directory, test.getFileName()));
    }
}
