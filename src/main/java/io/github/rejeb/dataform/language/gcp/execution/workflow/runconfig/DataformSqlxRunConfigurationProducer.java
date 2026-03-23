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
package io.github.rejeb.dataform.language.gcp.execution.workflow.runconfig;

import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.execution.actions.LazyRunConfigurationProducer;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import io.github.rejeb.dataform.language.compilation.DataformCompilationService;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.Mode;
import io.github.rejeb.dataform.language.gcp.settings.GcpRepositorySettings;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

import static io.github.rejeb.dataform.language.util.Utils.isActionFile;

public class DataformSqlxRunConfigurationProducer
        extends LazyRunConfigurationProducer<DataformWorkflowRunConfiguration> {

    @NotNull
    @Override
    public ConfigurationFactory getConfigurationFactory() {
        return ConfigurationTypeUtil
                .findConfigurationType(DataformWorkflowConfigurationType.class)
                .getConfigurationFactories()[0];
    }

    @Override
    protected boolean setupConfigurationFromContext(
            @NotNull DataformWorkflowRunConfiguration config,
            @NotNull ConfigurationContext context,
            @NotNull Ref<PsiElement> sourceElement) {

        VirtualFile file = getFile(context);
        if (file == null) return false;
        GcpRepositorySettings settings = GcpRepositorySettings.getInstance(context.getProject());
        config.setWorkspaceId(settings.getSelectedWorkspaceId());
        CompiledGraph compiledGraph = DataformCompilationService.getInstance(context.getProject()).getCompiledGraph();
        String canonicalPath = file.getCanonicalPath();
        config.setIncludedTargets(
                compiledGraph
                        .findCompiledQueryByFileName(canonicalPath)
                        .stream()
                        .flatMap(q -> Optional.ofNullable(q.tableName()).stream())
                        .toList());

        config.setName(file.getNameWithoutExtension());
        config.setSelectedMode(Mode.ACTIONS);
        return true;
    }

    @Override
    public boolean isConfigurationFromContext(
            @NotNull DataformWorkflowRunConfiguration config,
            @NotNull ConfigurationContext context) {

        VirtualFile file = getFile(context);
        if (file == null) return false;
        String actionName = file.getNameWithoutExtension();
        GcpRepositorySettings settings = GcpRepositorySettings.getInstance(context.getProject());
        config.setWorkspaceId(settings.getSelectedWorkspaceId());
        CompiledGraph compiledGraph = DataformCompilationService.getInstance(context.getProject()).getCompiledGraph();
        List<String> includedTargets = compiledGraph
                .findCompiledQueryByFileName(file.getCanonicalPath())
                .stream()
                .flatMap(q -> Optional.ofNullable(q.tableName()).stream()).toList();

        return actionName.equals(config.getName()) && config.getIncludedTargets().contains(includedTargets);
    }

    /**
     * Intercepte le lancement pour afficher la popup d'options avant d'exécuter.
     */
    @Override
    public void onFirstRun(@NotNull ConfigurationFromContext configFromContext,
                           @NotNull ConfigurationContext context,
                           @NotNull Runnable startRunnable) {

        VirtualFile file = getFile(context);
        if (file == null) {
            startRunnable.run();
            return;
        }

        DataformWorkflowRunConfiguration config =
                (DataformWorkflowRunConfiguration) configFromContext.getConfiguration();

        SqlxRunOptionsPopup.show(context.getProject(), (deps, dependants, fullRefresh) -> {
            config.setTransitiveDependenciesIncluded(deps);
            config.setTransitiveDependentsIncluded(dependants);
            config.setFullyRefreshIncrementalTables(fullRefresh);
            startRunnable.run();
        });
    }

    private static VirtualFile getFile(@NotNull ConfigurationContext context) {
        Location<?> location = context.getLocation();
        if (location == null) return null;
        PsiElement element = location.getPsiElement();
        PsiFile file = element.getContainingFile();
        if (file == null) return null;
        VirtualFile vf = file.getVirtualFile();
        if (vf == null) return null;
        return isActionFile(vf) ? vf : null;
    }
}