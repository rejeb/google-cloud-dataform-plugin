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

import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreter;
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;

public class NodeInterpreterManager {
    private static NodeInterpreterManager INSTANCE = null;
    private final Project project;
    private final Path nodeInstallDir;
    private final Path npmExecutable;
    private final Path nodeModulesDir;
    private final Path nodeBinDir;

    private NodeInterpreterManager(@NotNull Project project) {
        this.project = project;
        NodeInstallInfo nodeInstallInfo = loadNodeInstallInfo();
        this.nodeInstallDir = nodeInstallInfo.nodeInstallDir;
        this.npmExecutable = nodeInstallInfo.npmExecutable;
        this.nodeModulesDir = nodeInstallInfo.nodeModulesDir;
        this.nodeBinDir = nodeInstallInfo.nodeBinDir;
    }


    public synchronized static NodeInterpreterManager getInstance(@NotNull Project project) {
        if (INSTANCE == null|| INSTANCE.nodeInstallDir == null) {
            INSTANCE = new NodeInterpreterManager(project);
        }
        return INSTANCE;
    }

    public Path nodeInstallDir() {
        return nodeInstallDir;
    }

    public Path npmExecutable() {
        return npmExecutable;
    }

    public Path nodeModulesDir() {
        return nodeModulesDir;
    }

    public Path nodeBinDir() {
        return nodeBinDir;
    }

    private NodeInstallInfo loadNodeInstallInfo() {
        Optional<Path> nodeInstallDir = findNodeInstallDir();
        Optional<Path> binDir = nodeInstallDir.map(nodeDir -> SystemInfo.isWindows ? nodeDir : nodeDir.resolve("bin"));
        Optional<Path> npmExecutable = binDir
                .flatMap(this::findNpmInNodeInstall);
        String nodeModulesDir = SystemInfo.isWindows ? "node_modules" : "lib/node_modules";

        if (npmExecutable.isPresent()) {
            return new NodeInstallInfo(
                    nodeInstallDir.get(),
                    npmExecutable.get(),
                    nodeInstallDir.get().resolve(nodeModulesDir),
                    binDir.get()
            );
        } else {
            return new NodeInstallInfo();
        }
    }


    private Optional<Path> findNodeInstallDir() {
        Optional<File> nodeExecutableDir = Optional
                .ofNullable(NodeJsInterpreterManager.getInstance(project).getInterpreter())
                .map(NodeJsInterpreter::getReferenceName)
                .map(File::new)
                .map(File::getParentFile);
        if (SystemInfo.isWindows) {
            return nodeExecutableDir
                    .map(File::toPath);
        } else {
            return nodeExecutableDir
                    .map(File::getParentFile)
                    .map(File::toPath);
        }
    }

    private Optional<Path> findNpmInNodeInstall(Path binDir) {
        String[] npmCmds = SystemInfo.isWindows ? new String[]{"npm.cmd", "npm.bat"} : new String[]{"npm"};
        return Arrays.stream(npmCmds)
                .map(binDir::resolve)
                .filter(nodepath -> nodepath.toFile().exists())
                .findFirst();
    }

    record NodeInstallInfo(
            @Nullable Path nodeInstallDir,
            @Nullable Path npmExecutable,
            @Nullable Path nodeModulesDir,
            @Nullable Path nodeBinDir
    ) {
        public NodeInstallInfo() {
            this(null, null, null, null);
        }
    }
}
