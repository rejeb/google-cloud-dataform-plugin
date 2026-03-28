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

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import io.github.rejeb.dataform.language.settings.DataformToolsSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.terminal.settings.TerminalLocalOptions;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static io.github.rejeb.dataform.language.setup.DataformInstaller.findDataformLibRootDir;

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
        String configured = DataformToolsSettings.getInstance().getCliExecutablePath();
        if (!configured.isBlank()) {
            File f = new File(configured);
            return Optional.ofNullable(
                    LocalFileSystem.getInstance().findFileByIoFile(f.getParentFile())
            );
        }
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

        String configuredCliCmd = DataformToolsSettings.getInstance().getCliExecutablePath().replace("\\", "/");
        if (configuredCliCmd.isBlank()) return Optional.empty();

        Path nodeBinDir = NodeInterpreterManager.getInstance(project).nodeBinDir();
        String pathEnv = (nodeBinDir != null
                ? nodeBinDir.toAbsolutePath() + File.pathSeparator
                : "") + System.getenv("PATH");

        String shellPath = TerminalLocalOptions.getInstance().getShellPath();
        if ((shellPath == null || shellPath.isBlank()) && SystemInfo.isWindows) {
            shellPath = findGitBash().orElse(null);
        }
        if (shellPath == null || shellPath.isBlank()) {
            GeneralCommandLine cmd = new GeneralCommandLine(configuredCliCmd, "compile", "--json")
                    .withWorkDirectory(project.getBasePath());
            cmd.getEnvironment().put("PATH", pathEnv);

            return Optional.of(cmd);
        }

        shellPath = shellPath.replace("\"", "").replace("\\", "/");

        if (SystemInfo.isWindows) {
            GeneralCommandLine cmd = buildWindowsCommand(shellPath, configuredCliCmd);
            cmd.getEnvironment().put("PATH", pathEnv);
            return Optional.of(cmd);
        } else {
            GeneralCommandLine cmd = buildUnixCommand(shellPath, configuredCliCmd);
            cmd.getEnvironment().put("PATH", pathEnv);
            return Optional.of(cmd);
        }

    }

    private GeneralCommandLine buildWindowsCommand(String shellPath, String configuredCliCmd) {
        String shellCmd = String.format("\"%s\" compile --json", configuredCliCmd);

        GeneralCommandLine cmd = new GeneralCommandLine(shellPath, "-c", shellCmd)
                .withWorkDirectory(project.getBasePath());

        return cmd;
    }

    private GeneralCommandLine buildUnixCommand(String shellPath, String configuredCliCmd) {
        return new GeneralCommandLine(shellPath, "-c", configuredCliCmd, "compile", "--json")
                .withWorkDirectory(project.getBasePath());
    }

    private Optional<String> findGitBash() {
        List<Path> candidates = List.of(
                Path.of("C:\\Program Files\\Git\\bin\\bash.exe"),
                Path.of("C:\\Program Files (x86)\\Git\\bin\\bash.exe"),
                Path.of(System.getProperty("user.home"), "AppData", "Local", "Programs", "Git", "bin", "bash.exe")
        );

        return candidates.stream()
                .filter(p -> p.toFile().exists())
                .findFirst()
                .map(p -> p.toFile().getAbsolutePath());
    }
}
