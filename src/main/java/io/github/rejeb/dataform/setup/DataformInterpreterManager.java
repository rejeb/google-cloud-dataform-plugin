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
package io.github.rejeb.dataform.setup;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.Optional;


public class DataformInterpreterManager {
    private static DataformInterpreterManager INSTANCE = null;
    private final Project project;
    private final Optional<VirtualFile> dataformCorePath;
    private final Optional<VirtualFile> dataformCliDir;
    private final String currentDataformCoreVersion;

    private DataformInterpreterManager(@NotNull Project project) {
        this.project = project;
        Optional<Path> dataformLibRootDir = findDataformLibRootDir();
        this.dataformCorePath = dataformLibRootDir
                .map(dir -> dir.resolve("core"))
                .map(dir -> LocalFileSystem.getInstance().findFileByIoFile(dir.toFile()));
        this.dataformCliDir = dataformLibRootDir
                .map(dir -> dir.resolve("cli"))
                .map(dir -> LocalFileSystem.getInstance().findFileByIoFile(dir.toFile()));
        this.currentDataformCoreVersion = getGlobalDataformVersion(this.dataformCorePath);
    }

    public synchronized static DataformInterpreterManager getInstance(@NotNull Project project) {
        if (INSTANCE == null || INSTANCE.dataformCorePath().isEmpty()) {
            INSTANCE = new DataformInterpreterManager(project);
        }
        return INSTANCE;
    }

    public Optional<VirtualFile> dataformCorePath() {
        return dataformCorePath;
    }

    public Optional<VirtualFile> dataformCliDir() {
        return dataformCliDir;
    }

    public String currentDataformCoreVersion() {
        return currentDataformCoreVersion;
    }

    private String getGlobalDataformVersion(Optional<VirtualFile> corePackage) {
        if (corePackage.isEmpty()) return null;

        VirtualFile packageJson = corePackage.get().findChild("package.json");
        if (packageJson == null) return null;

        try {
            String content = VfsUtil.loadText(packageJson);
            JsonObject json = JsonParser.parseString(content).getAsJsonObject();
            return json.get("version").getAsString();
        } catch (Exception e) {
            return null;
        }
    }

    private Optional<Path> findDataformLibRootDir() {
        NodeInterpreterManager nodeInterpreterManager = NodeInterpreterManager.getInstance(project);
        Path nodeModulesDir = nodeInterpreterManager.nodeModulesDir();
        if (nodeModulesDir == null) {
            return Optional.empty();
        }
        Path dataformRootDir = nodeModulesDir.resolve("@dataform");
        Path dataformCoreDir = dataformRootDir.resolve("core");
        Path dataformCliDir = dataformRootDir.resolve("cli");
        if (dataformCoreDir.toFile().exists() && dataformCliDir.toFile().exists()) {
            return Optional.of(dataformRootDir);
        } else {
            return Optional.empty();
        }
    }


}
