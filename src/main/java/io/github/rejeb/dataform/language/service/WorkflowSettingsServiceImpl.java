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
package io.github.rejeb.dataform.language.service;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import io.github.rejeb.dataform.language.util.DataformProjectLayout;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.YAMLDocument;
import org.jetbrains.yaml.psi.YAMLFile;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLMapping;

import java.util.*;

import static io.github.rejeb.dataform.language.service.WorkflowSettingsYamlFileWrapper.DATAFORM_KEY;
import static io.github.rejeb.dataform.language.service.WorkflowSettingsYamlFileWrapper.PROJECT_CONFIG_KEY;

public final class WorkflowSettingsServiceImpl implements WorkflowSettingsService {
    private final Project project;

    private record Snapshot(@NotNull Map<String, WorkflowSettingsProperty> properties,
                            long stamp,
                            @NotNull String url) {
    }

    private volatile Snapshot snapshot;
    private volatile VirtualFile cachedSettingsFile;

    public WorkflowSettingsServiceImpl(Project project) {
        this.project = project;
    }

    public static WorkflowSettingsServiceImpl getInstance(Project project) {
        return project.getService(WorkflowSettingsServiceImpl.class);
    }

    @Override
    public @NotNull Map<String, WorkflowSettingsProperty> getWorkflowProperties() {
        return getWorkflowProperties(null);
    }

    @Override
    public @NotNull Map<String, WorkflowSettingsProperty> getWorkflowProperties(@Nullable VirtualFile context) {
        YAMLFile originalFile = findOriginalWorkflowSettingsFile(context);
        if (originalFile == null) {
            snapshot = null;
            return Collections.emptyMap();
        }

        long stamp = originalFile.getModificationStamp();
        String url = fileUrlOf(originalFile);
        Snapshot current = snapshot;
        if (current != null && current.stamp() == stamp && current.url().equals(url)) {
            return current.properties();
        }

        Map<String, WorkflowSettingsProperty> properties =
                parseWorkflowSettings(WorkflowSettingsYamlFileWrapper.create(originalFile, project));
        snapshot = url == null ? null : new Snapshot(properties, stamp, url);
        return properties;
    }

    @Nullable
    private YAMLFile findOriginalWorkflowSettingsFile(@Nullable VirtualFile context) {
        if (DumbService.isDumb(project)) {
            return null;
        }
        VirtualFile file = findWorkflowSettingsVirtualFile(context);
        if (file == null) {
            return null;
        }
        PsiFile psiFile = ApplicationManager.getApplication()
                .runReadAction((Computable<PsiFile>) () -> PsiManager.getInstance(project).findFile(file));
        return psiFile instanceof YAMLFile yamlFile ? yamlFile : null;
    }

    @Override
    @Nullable
    public VirtualFile findWorkflowSettingsVirtualFile() {
        return findWorkflowSettingsVirtualFile(null);
    }

    /**
     * Locates {@code workflow_settings.yaml} by walking up from the given file, then from the project
     * directory, and only as a last resort through the filename index.
     *
     * <p>The directory walk keeps the lookup free of index access, which is prohibited on the EDT
     * where code folding may be computed.</p>
     */
    @Override
    @Nullable
    public VirtualFile findWorkflowSettingsVirtualFile(@Nullable VirtualFile context) {
        VirtualFile fromContext = DataformProjectLayout.findWorkflowSettings(context);
        if (fromContext != null) {
            cachedSettingsFile = fromContext;
            return fromContext;
        }

        VirtualFile cached = cachedSettingsFile;
        if (cached != null && cached.isValid() && isConsistentWith(cached, context)) {
            return cached;
        }

        VirtualFile fromProjectDir = walkDown(ProjectUtil.guessProjectDir(project));
        if (fromProjectDir != null) {
            cachedSettingsFile = fromProjectDir;
            return fromProjectDir;
        }

        if (ApplicationManager.getApplication().isDispatchThread()) {
            return null;
        }
        VirtualFile indexed = ReadAction.nonBlocking(() -> {
            Collection<VirtualFile> files = FilenameIndex.getVirtualFilesByName(
                    DataformProjectLayout.WORKFLOW_SETTINGS_YAML, GlobalSearchScope.projectScope(project));
            return files.isEmpty() ? null : files.iterator().next();
        }).executeSynchronously();
        cachedSettingsFile = indexed;
        return indexed;
    }

    private static boolean isConsistentWith(@NotNull VirtualFile cached, @Nullable VirtualFile context) {
        if (context == null) {
            return true;
        }
        VirtualFile root = cached.getParent();
        return root != null && VfsUtilCore.isAncestor(root, context, false);
    }

    @Nullable
    private VirtualFile walkDown(@Nullable VirtualFile directory) {
        if (directory == null) {
            return null;
        }
        VirtualFile candidate = directory.findChild(DataformProjectLayout.WORKFLOW_SETTINGS_YAML);
        return candidate != null && !candidate.isDirectory() ? candidate : null;
    }

    @Nullable
    private static String fileUrlOf(@NotNull YAMLFile file) {
        VirtualFile virtualFile = file.getVirtualFile();
        return virtualFile == null ? null : virtualFile.getUrl();
    }

    @NotNull
    public Collection<String> getLeafPathsForPrefix(@Nullable String prefix) {
        List<String> paths = new ArrayList<>();
        collectLeafPaths(propertiesForPrefix(prefix), "", paths);
        return paths;
    }

    private void collectLeafPaths(@NotNull Map<String, WorkflowSettingsProperty> properties,
                                  @NotNull String parentPath,
                                  @NotNull List<String> paths) {
        properties.forEach((name, property) -> {
            String path = parentPath.isEmpty() ? name : parentPath + "." + name;
            if (property.hasChildren()) {
                collectLeafPaths(property.children(), path, paths);
            } else {
                paths.add(path);
            }
        });
    }

    @NotNull
    private Map<String, WorkflowSettingsProperty> propertiesForPrefix(@Nullable String prefix) {
        Map<String, WorkflowSettingsProperty> current = getWorkflowProperties();
        if (prefix == null || prefix.isEmpty()) {
            return current;
        }
        for (String part : prefix.split("\\.")) {
            WorkflowSettingsProperty property = current.get(part);
            if (property == null || !property.hasChildren()) {
                return Collections.emptyMap();
            }
            current = property.children();
        }
        return current;
    }

    @NotNull
    public Collection<String> getPropertiesForPrefix(@Nullable String prefix) {
        return propertiesForPrefix(prefix).keySet();
    }

    @Nullable
    public WorkflowSettingsProperty getProperty(@Nullable String propKey) {
        if (propKey == null || propKey.isEmpty()) {
            return null;
        }

        return Arrays
                .stream(propKey.split("\\."))
                .reduce(
                        getOriginalFileProps(),
                        (current, key) -> {

                            if (current == null || !current.hasChildren()) {
                                return null;
                            }
                            return current.children().get(key);
                        },
                        (left, right) -> right
                );

    }

    @Override
    public boolean isWorkflowSettingProperty(@NotNull String property) {
        return isWorkflowSettingProperty(property,getWorkflowProperties().get(DATAFORM_KEY));
    }

    private boolean isWorkflowSettingProperty(@NotNull String propertyToFind, @NotNull WorkflowSettingsProperty property) {
            if(propertyToFind.equals(property.name())){
                return true;
            }else if(property.hasChildren()){
                for (WorkflowSettingsProperty child : property.children().values()) {
                    if(isWorkflowSettingProperty(propertyToFind, child)){
                        return true;
                    }
                }
            }

        return false;
    }

    @Nullable
    public WorkflowSettingsYamlFileWrapper findWorkflowSettingsFile() {
        YAMLFile originalFile = findOriginalWorkflowSettingsFile(null);
        return originalFile == null ? null : WorkflowSettingsYamlFileWrapper.create(originalFile, project);
    }

    @NotNull
    private Map<String, WorkflowSettingsProperty> parseWorkflowSettings(@NotNull WorkflowSettingsYamlFileWrapper yamlFile) {
        Map<String, WorkflowSettingsProperty> result = new HashMap<>();

        YAMLDocument document = yamlFile.getDocuments().isEmpty() ? null : yamlFile.getDocuments().get(0);
        if (document == null) {
            return result;
        }

        if (document.getTopLevelValue() instanceof YAMLMapping mapping) {
            for (YAMLKeyValue keyValue : mapping.getKeyValues()) {
                String key = keyValue.getKeyText();
                WorkflowSettingsProperty property = parseProperty(keyValue, yamlFile);
                result.put(key, property);
            }
        }

        return result;
    }

    @NotNull
    private WorkflowSettingsProperty parseProperty(@NotNull YAMLKeyValue keyValue, @NotNull WorkflowSettingsYamlFileWrapper yamlFile) {
        String key = keyValue.getKeyText();
        String value = keyValue.getValueText();

        if (keyValue.getValue() instanceof YAMLMapping mapping) {
            Map<String, WorkflowSettingsProperty> children = new HashMap<>();
            for (YAMLKeyValue child : mapping.getKeyValues()) {
                children.put(child.getKeyText(), parseProperty(child, yamlFile));
            }
            YAMLKeyValue wrappedKeyValue = yamlFile.getWrappedKeyValue(keyValue);
            return new WorkflowSettingsProperty(key, null, wrappedKeyValue, children);
        }
        YAMLKeyValue wrappedKeyValue = yamlFile.getWrappedKeyValue(keyValue);
        return new WorkflowSettingsProperty(key, value, wrappedKeyValue, null);
    }


    @Nullable
    private WorkflowSettingsProperty getOriginalFileProps() {
        Map<String, WorkflowSettingsProperty> children = this.getWorkflowProperties().get(DATAFORM_KEY)
                .children();
        return children != null ? children.get(PROJECT_CONFIG_KEY) : null;
    }


}
