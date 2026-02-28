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
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.file.Path;
import java.util.Optional;

@Service(Service.Level.PROJECT)
public final class DataformInterpreterManager {
    private final Project project;
    private Optional<VirtualFile> dataformCorePath;
    private Optional<VirtualFile> dataformCliDir;
    private String currentDataformCoreVersion;

    private DataformInterpreterManager(@NotNull Project project) {
        this.project = project;
        init();
    }

    private void init() {
        Optional<Path> dataformLibRootDir = findDataformLibRootDir();
        this.dataformCorePath = dataformLibRootDir
                .map(dir -> dir.resolve("core"))
                .map(dir -> LocalFileSystem.getInstance().findFileByIoFile(dir.toFile()));
        this.dataformCliDir = dataformLibRootDir
                .map(dir -> dir.resolve("cli"))
                .map(dir -> LocalFileSystem.getInstance().findFileByIoFile(dir.toFile()));
        this.currentDataformCoreVersion = getGlobalDataformVersion(this.dataformCorePath);
    }

    public Optional<VirtualFile> dataformCorePath() {
        if (dataformCorePath.isEmpty()) {
            init();
        }
        return dataformCorePath;
    }

    public Optional<VirtualFile> dataformCliDir() {
        if (dataformCorePath.isEmpty()) {
            init();
        }
        return dataformCliDir;
    }

    public String currentDataformCoreVersion() {
        return currentDataformCoreVersion;
    }

    public Optional<GeneralCommandLine> buildDataformCompileCommand() {
        String dataformCliCmd = SystemInfo.isWindows ? "dataform.cmd" : "dataform";
        Path nodeBinDir = NodeInterpreterManager
                .getInstance(project)
                .nodeBinDir();
        return Optional.ofNullable(nodeBinDir).map(binDir -> {
            String dataformExecutable = binDir.resolve(dataformCliCmd).toAbsolutePath().toString();
            GeneralCommandLine cmd = new GeneralCommandLine(dataformExecutable, "compile", "--json")
                    .withWorkDirectory(project.getBasePath());
            String pathEnv = nodeBinDir.toFile().getAbsolutePath() + File.pathSeparator +
                    System.getenv("PATH");
            cmd.getEnvironment().put("PATH", pathEnv);
            return cmd;
        });
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
