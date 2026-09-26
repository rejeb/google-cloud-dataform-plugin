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
package io.github.rejeb.dataform.language.setup;

import com.intellij.json.psi.JsonElementGenerator;
import com.intellij.json.psi.JsonFile;
import com.intellij.json.psi.JsonObject;
import com.intellij.json.psi.JsonProperty;
import com.intellij.json.psi.JsonPsiUtil;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.YAMLUtil;
import org.jetbrains.yaml.psi.YAMLFile;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLMapping;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public final class DataformCoreVersionMigratorImpl implements DataformCoreVersionMigrator {

    private static final Logger LOG = Logger.getInstance(DataformCoreVersionMigratorImpl.class);
    private static final String COMMAND_NAME = "Move Dataform Core Version to package.json";

    private final Project project;

    public DataformCoreVersionMigratorImpl(@NotNull Project project) {
        this.project = project;
    }

    @Override
    public Optional<String> findDeclaredCoreVersion(@NotNull VirtualFile workflowSettings) {
        return Optional.ofNullable(findCoreVersionKey(workflowSettings))
                .map(YAMLKeyValue::getValueText)
                .map(String::trim)
                .filter(version -> !version.isEmpty());
    }

    @Override
    public boolean migrate(@NotNull VirtualFile workflowSettings) {
        VirtualFile projectDir = workflowSettings.getParent();
        YAMLKeyValue coreVersionKey = findCoreVersionKey(workflowSettings);
        if (projectDir == null || coreVersionKey == null
                || !(coreVersionKey.getParentMapping() instanceof YAMLMapping mapping)) {
            return false;
        }
        String version = coreVersionKey.getValueText().trim();
        if (version.isEmpty()) {
            return false;
        }

        VirtualFile existingPackageJson = projectDir.findChild(DataformPackageJson.FILE_NAME);
        JsonObject packageRoot = existingPackageJson == null ? null : packageJsonRoot(existingPackageJson);
        if (existingPackageJson != null && !canDeclareCoreDependency(packageRoot)) {
            LOG.warn("package.json dependencies cannot be edited, Dataform core version left in workflow_settings.yaml");
            return false;
        }

        PsiFile yamlFile = coreVersionKey.getContainingFile();
        try {
            WriteCommandAction.writeCommandAction(project, yamlFile)
                    .withName(COMMAND_NAME)
                    .run(() -> {
                        if (packageRoot == null) {
                            createPackageJson(projectDir, version);
                        } else {
                            declareCoreDependency(packageRoot, version);
                        }
                        mapping.deleteKeyValue(coreVersionKey);
                    });
        } catch (IOException e) {
            LOG.warn("Unable to create package.json", e);
            return false;
        }

        saveDocument(workflowSettings);
        VirtualFile packageJson = projectDir.findChild(DataformPackageJson.FILE_NAME);
        if (packageJson != null) {
            saveDocument(packageJson);
        }
        DataformPackageInstaller.getInstance(project).installAsync(projectDir);
        return true;
    }

    @Nullable
    private YAMLKeyValue findCoreVersionKey(@NotNull VirtualFile workflowSettings) {
        if (!workflowSettings.isValid()) {
            return null;
        }
        PsiFile psiFile = PsiManager.getInstance(project).findFile(workflowSettings);
        if (!(psiFile instanceof YAMLFile yamlFile)) {
            return null;
        }
        return YAMLUtil.getQualifiedKeyInFile(yamlFile, CORE_VERSION_KEY);
    }

    @Nullable
    private JsonObject packageJsonRoot(@NotNull VirtualFile packageJson) {
        PsiFile psiFile = PsiManager.getInstance(project).findFile(packageJson);
        if (psiFile instanceof JsonFile jsonFile && jsonFile.getTopLevelValue() instanceof JsonObject root) {
            return root;
        }
        return null;
    }

    private void createPackageJson(@NotNull VirtualFile projectDir, @NotNull String version) throws IOException {
        VirtualFile packageJson = projectDir.createChildData(this, DataformPackageJson.FILE_NAME);
        packageJson.setBinaryContent(DataformPackageJson.content(version).getBytes(StandardCharsets.UTF_8));
    }

    private static boolean canDeclareCoreDependency(@Nullable JsonObject packageRoot) {
        if (packageRoot == null) {
            return false;
        }
        JsonProperty dependencies = packageRoot.findProperty(DataformPackageJson.DEPENDENCIES_KEY);
        return dependencies == null || dependencies.getValue() instanceof JsonObject;
    }

    private void declareCoreDependency(@NotNull JsonObject packageRoot, @NotNull String version) {
        JsonElementGenerator generator = new JsonElementGenerator(project);
        String versionLiteral = "\"" + version + "\"";
        JsonProperty dependencies = packageRoot.findProperty(DataformPackageJson.DEPENDENCIES_KEY);
        if (dependencies == null) {
            JsonProperty created = generator.createProperty(DataformPackageJson.DEPENDENCIES_KEY,
                    "{\"" + DataformPackageJson.CORE_PACKAGE + "\": " + versionLiteral + "}");
            JsonPsiUtil.addProperty(packageRoot, created, false);
            return;
        }
        if (dependencies.getValue() instanceof JsonObject dependenciesObject
                && dependenciesObject.findProperty(DataformPackageJson.CORE_PACKAGE) == null) {
            JsonPsiUtil.addProperty(dependenciesObject,
                    generator.createProperty(DataformPackageJson.CORE_PACKAGE, versionLiteral),
                    false);
        }
    }

    private void saveDocument(@NotNull VirtualFile file) {
        Document document = FileDocumentManager.getInstance().getCachedDocument(file);
        if (document == null) {
            return;
        }
        PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(document);
        FileDocumentManager.getInstance().saveDocument(document);
    }
}
