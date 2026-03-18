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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import io.github.rejeb.dataform.settings.DataformToolsSettings;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static io.github.rejeb.dataform.setup.DataformInstaller.findDataformLibRootDir;

public final class DataformInterpreterManagerImpl implements DataformInterpreterManager {

    private final Project project;

    public DataformInterpreterManagerImpl(@NotNull Project project) {
        this.project = project;
    }

    @Override
    public Optional<VirtualFile> dataformCorePath() {
        String configured = DataformToolsSettings.getInstance().getCoreInstallPath();
        if (!configured.isBlank()) {
            return Optional.ofNullable(
                    LocalFileSystem.getInstance().findFileByIoFile(new File(configured))
            );
        }

        return findDataformLibRootDir(NodeInterpreterManager.getInstance(project))
                .map(dir -> dir.resolve("core"))
                .map(dir -> LocalFileSystem.getInstance().findFileByIoFile(dir.toFile()));
    }

    @Override
    public Optional<VirtualFile> dataformCliDir() {
        // 1. Priorité : settings utilisateur
        String configured = DataformToolsSettings.getInstance().getCliExecutablePath();
        if (!configured.isBlank()) {
            File f = new File(configured);
            return Optional.ofNullable(
                    LocalFileSystem.getInstance().findFileByIoFile(f.getParentFile())
            );
        }
        // 2. Fallback : détection automatique
        return findDataformLibRootDir(NodeInterpreterManager.getInstance(project))
                .map(dir -> dir.resolve("cli"))
                .map(dir -> LocalFileSystem.getInstance().findFileByIoFile(dir.toFile()));
    }

    @Override
    public String currentDataformCoreVersion() {
        return dataformCorePath().map(coreDir -> {
            VirtualFile packageJson = coreDir.findChild("package.json");
            if (packageJson == null) return null;
            try {
                String content = VfsUtil.loadText(packageJson);
                JsonObject json = JsonParser.parseString(content).getAsJsonObject();
                return json.get("version").getAsString();
            } catch (Exception e) {
                return null;
            }
        }).orElse(null);
    }

    @Override
    public Optional<GeneralCommandLine> buildDataformCompileCommand() {
        Path nodeBinDir = NodeInterpreterManager.getInstance(project).nodeBinDir();
        return Optional.ofNullable(nodeBinDir).map(binDir -> {
            String pathEnv = binDir.toFile().getAbsolutePath()
                    + File.pathSeparator + System.getenv("PATH");

            if (SystemInfo.isWindows) {
                Optional<Path> gitBash = findGitBash();
                if (gitBash.isPresent()) {
                    return buildGitBashCommand(gitBash.get(), binDir, pathEnv);
                }
                String exe = binDir.resolve("dataform.cmd").toAbsolutePath().toString();
                GeneralCommandLine cmd = new GeneralCommandLine(exe, "compile", "--json")
                        .withWorkDirectory(project.getBasePath());
                cmd.getEnvironment().put("PATH", pathEnv);
                return cmd;
            }

            String exe = binDir.resolve("dataform").toAbsolutePath().toString();
            GeneralCommandLine cmd = new GeneralCommandLine(exe, "compile", "--json")
                    .withWorkDirectory(project.getBasePath());
            cmd.getEnvironment().put("PATH", pathEnv);
            return cmd;
        });
    }

    private Optional<Path> findGitBash() {
        return List.of(
                Path.of("C:\\Program Files\\Git\\bin\\bash.exe"),
                Path.of("C:\\Program Files (x86)\\Git\\bin\\bash.exe"),
                Path.of(System.getProperty("user.home"),
                        "AppData", "Local", "Programs", "Git", "bin", "bash.exe")
        ).stream().filter(p -> p.toFile().exists()).findFirst();
    }

    private GeneralCommandLine buildGitBashCommand(Path bashExe, Path nodeBinDir,
                                                   String winPathEnv) {
        String posixDir = nodeBinDir.toAbsolutePath().toString()
                .replace("\\", "/")
                .replaceFirst("^([A-Za-z]):", "/$1")
                .toLowerCase(Locale.ROOT);
        String bashCmd = String.format(
                "export PATH=\"%s:$PATH\"; dataform compile --json", posixDir);
        GeneralCommandLine cmd = new GeneralCommandLine(
                bashExe.toAbsolutePath().toString(), "-c", bashCmd)
                .withWorkDirectory(project.getBasePath());
        cmd.getEnvironment().put("PATH", winPathEnv);
        return cmd;
    }
}
