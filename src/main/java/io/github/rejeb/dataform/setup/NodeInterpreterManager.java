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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.stream.Stream;

public class NodeInterpreterManager {
    private static NodeInterpreterManager INSTANCE =null;
    private final Project project;
    private final Path nodeInstallDir;
    private final Path npmExecutable;
    private final Path nodeModulesDir;

    private NodeInterpreterManager(@NotNull Project project) {
        this.project = project;
        NodeInstallInfo nodeInstallInfo = loadNodeInstallInfo();
        this.nodeInstallDir = nodeInstallInfo.nodeInstallDir;
        this.npmExecutable = nodeInstallInfo.npmExecutable;
        this.nodeModulesDir = nodeInstallInfo.nodeModulesDir;
    }


    public synchronized static NodeInterpreterManager getInstance(@NotNull Project project) {
        if (INSTANCE == null) {
            INSTANCE = new NodeInterpreterManager(project);
        }
        return INSTANCE;
    }

    public Path getNodeInstallDir() {
        return nodeInstallDir;
    }

    public Path getNpmExecutable() {
        return npmExecutable;
    }

    public Path getNodeModulesDir() {
        return nodeModulesDir;
    }

    private NodeInstallInfo loadNodeInstallInfo() {
        Optional<Path> nodeInstallDir = findNodeInstallDir();
        Optional<Path> npmExecutable = nodeInstallDir
                .flatMap(this::findNpmInNodeInstall);
        if (npmExecutable.isPresent()) {
            return new NodeInstallInfo(
                    nodeInstallDir.get(),
                    npmExecutable.get(),
                    nodeInstallDir.get().resolve("lib/node_modules")
            );
        } else {
            return new NodeInstallInfo();
        }
    }


    private Optional<Path> findNodeInstallDir() {
        return Optional
                .ofNullable(NodeJsInterpreterManager.getInstance(project).getInterpreter())
                .map(NodeJsInterpreter::getReferenceName)
                .map(File::new)
                .map(File::getParentFile)
                .map(File::getParent)
                .map(Paths::get);
    }

    private Optional<Path> findNpmInNodeInstall(Path nodeDir) {
        Path binDir = nodeDir.resolve("bin");
        return Stream.of("npm", "npm.cmd", "npm.bat")
                .map(binDir::resolve)
                .filter(nodepath -> nodepath.toFile().exists())
                .findFirst();
    }

    record NodeInstallInfo(
            @Nullable Path nodeInstallDir,
            @Nullable Path npmExecutable,
            @Nullable Path nodeModulesDir
    ) {
        public NodeInstallInfo() {
            this(null, null, null);
        }
    }
}
