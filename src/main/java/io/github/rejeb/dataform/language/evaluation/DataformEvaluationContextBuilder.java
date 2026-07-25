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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import io.github.rejeb.dataform.language.compilation.DataformCompilationService;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.compilation.model.ProjectConfig;
import io.github.rejeb.dataform.language.compilation.model.Target;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import io.github.rejeb.dataform.language.index.DataformJsFileIndex;
import io.github.rejeb.dataform.language.psi.SqlxJsBlock;
import io.github.rejeb.dataform.language.service.WorkflowSettingsProperty;
import io.github.rejeb.dataform.language.service.WorkflowSettingsService;
import io.github.rejeb.dataform.language.setup.NodeInterpreterManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Assembles the Dataform evaluation environment from the cached compiled graph, the workflow
 * settings and the project include files.
 *
 * <p>Must be called inside a read action. Never triggers a compilation.</p>
 */
public final class DataformEvaluationContextBuilder {

    private static final Map<String, String> SETTINGS_TO_PROJECT_CONFIG = Map.of(
            "defaultProject", "defaultDatabase",
            "defaultDataset", "defaultSchema",
            "defaultLocation", "defaultLocation",
            "defaultAssertionDataset", "assertionSchema",
            "assertionSchema", "assertionSchema");

    private DataformEvaluationContextBuilder() {
    }

    /**
     * Builds the evaluation context for the given file.
     */
    @NotNull
    public static DataformEvaluationContext build(@NotNull Project project, @NotNull PsiFile file) {
        CompiledGraph graph = DataformCompilationService.getInstance(project).getCompiledGraph();
        return new DataformEvaluationContext(
                projectConfig(project, graph, file),
                refTargets(graph),
                selfTarget(graph, file),
                includeSources(project),
                fileScript(file),
                nodePaths(project));
    }

    @NotNull
    private static Map<String, Object> projectConfig(@NotNull Project project,
                                                     @Nullable CompiledGraph graph,
                                                     @NotNull PsiFile file) {
        Map<String, Object> config = new HashMap<>(fromWorkflowSettings(project, file));
        ProjectConfig compiled = graph == null ? null : graph.getProjectConfig();
        if (compiled != null) {
            putIfNotNull(config, "defaultDatabase", compiled.getDefaultDatabase());
            putIfNotNull(config, "defaultSchema", compiled.getDefaultSchema());
            putIfNotNull(config, "assertionSchema", compiled.getAssertionSchema());
            putIfNotNull(config, "defaultLocation", compiled.getDefaultLocation());
            putIfNotNull(config, "warehouse", compiled.getWarehouse());
            mergeVars(config, compiled.getVars());
        }
        config.putIfAbsent("vars", new HashMap<String, Object>());
        return config;
    }

    @SuppressWarnings("unchecked")
    private static void mergeVars(@NotNull Map<String, Object> config, @Nullable Map<String, Object> compiledVars) {
        if (compiledVars == null || compiledVars.isEmpty()) {
            return;
        }
        Object existing = config.get("vars");
        Map<String, Object> vars = existing instanceof Map
                ? new HashMap<>((Map<String, Object>) existing)
                : new HashMap<>();
        vars.putAll(compiledVars);
        config.put("vars", vars);
    }

    @Nullable
    private static String fileScript(@NotNull PsiFile file) {
        Collection<SqlxJsBlock> blocks = PsiTreeUtil.findChildrenOfType(file, SqlxJsBlock.class);
        if (blocks.isEmpty()) {
            return null;
        }
        return blocks.stream().map(PsiElement::getText).collect(Collectors.joining("\n"));
    }

    @NotNull
    private static Map<String, Object> fromWorkflowSettings(@NotNull Project project, @NotNull PsiFile file) {
        Map<String, Object> config = new HashMap<>();
        Map<String, WorkflowSettingsProperty> properties =
                WorkflowSettingsService.getInstance(project).getWorkflowProperties(file.getVirtualFile());
        WorkflowSettingsProperty dataform = properties.get("dataform");
        Map<String, WorkflowSettingsProperty> settings =
                dataform == null || dataform.children() == null ? Map.of() : dataform.children();
        WorkflowSettingsProperty projectConfig = settings.get("projectConfig");
        if (projectConfig == null || projectConfig.children() == null) {
            return config;
        }
        projectConfig.children().forEach((name, property) -> {
            String target = SETTINGS_TO_PROJECT_CONFIG.getOrDefault(name, name);
            if (property.hasChildren()) {
                config.put(target, toPlainMap(property.children()));
            } else if (property.value() != null) {
                config.put(target, property.value());
            }
        });
        return config;
    }

    @NotNull
    private static Map<String, Object> toPlainMap(@NotNull Map<String, WorkflowSettingsProperty> properties) {
        Map<String, Object> plain = new HashMap<>();
        properties.forEach((name, property) -> {
            if (property.hasChildren()) {
                plain.put(name, toPlainMap(property.children()));
            } else if (property.value() != null) {
                plain.put(name, property.value());
            }
        });
        return plain;
    }

    @NotNull
    private static Map<String, String> refTargets(@Nullable CompiledGraph graph) {
        Map<String, String> targets = new LinkedHashMap<>();
        if (graph == null) {
            return targets;
        }
        graph.getDeclarations().forEach(declaration -> put(targets, declaration.getTarget()));
        graph.getOperations().forEach(operation -> put(targets, operation.getTarget()));
        graph.getAssertions().forEach(assertion -> put(targets, assertion.getTarget()));
        graph.getTables().forEach(table -> put(targets, table.getTarget()));
        return targets;
    }

    private static void put(@NotNull Map<String, String> targets, @Nullable Target target) {
        if (target == null || target.getName() == null) {
            return;
        }
        targets.put(target.getName(), target.getFullName());
    }

    @Nullable
    private static String selfTarget(@Nullable CompiledGraph graph, @NotNull PsiFile file) {
        VirtualFile virtualFile = file.getVirtualFile();
        if (graph == null || virtualFile == null) {
            return null;
        }
        return graph.findTargetByRefName(virtualFile.getNameWithoutExtension())
                .map(Target::getFullName)
                .orElse(null);
    }

    @NotNull
    private static Map<String, String> includeSources(@NotNull Project project) {
        Map<String, String> sources = new LinkedHashMap<>();
        for (PsiFile include : DataformJsFileIndex.findDataformJsFiles(project)) {
            VirtualFile virtualFile = include == null ? null : include.getVirtualFile();
            if (virtualFile != null) {
                sources.put(virtualFile.getNameWithoutExtension(), include.getText());
            }
        }
        return sources;
    }

    @NotNull
    private static List<String> nodePaths(@NotNull Project project) {
        List<String> paths = new ArrayList<>();
        Path globalModules = NodeInterpreterManager.getInstance(project).nodeModulesDir();
        if (globalModules != null) {
            paths.add(globalModules.toString());
        }
        String basePath = project.getBasePath();
        if (basePath != null) {
            paths.add(Path.of(basePath, "node_modules").toString());
        }
        return paths;
    }

    private static void putIfNotNull(@NotNull Map<String, Object> config, @NotNull String key, @Nullable Object value) {
        if (value != null) {
            config.put(key, value);
        }
    }
}
