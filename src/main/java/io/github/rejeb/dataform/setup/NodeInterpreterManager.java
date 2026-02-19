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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import io.github.rejeb.dataform.language.util.NodeJsNpmUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
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
        if (INSTANCE == null || INSTANCE.nodeInstallDir == null) {
            INSTANCE = new NodeInterpreterManager(project);
        }
        return INSTANCE;
    }

    @Nullable
    public Path nodeInstallDir() {
        return nodeInstallDir;
    }

    @Nullable
    public Path npmExecutable() {
        if (npmExecutable == null) {
            loadNodeInstallInfo();
        }
        return npmExecutable;
    }

    @Nullable
    public Path nodeModulesDir() {
        if (npmExecutable == null) {
            loadNodeInstallInfo();
        }
        return nodeModulesDir;
    }

    @Nullable
    public Path nodeBinDir() {
        if (npmExecutable == null) {
            loadNodeInstallInfo();
        }
        return nodeBinDir;
    }

    private NodeInstallInfo loadNodeInstallInfo() {
        Optional<Path> npmExecutable = NodeJsNpmUtils.findValidNpmPath(project);
        Optional<Path> prefixDir = npmExecutable.flatMap(npmExec -> NodeJsNpmUtils.findNodeInstallDir(project, npmExec));
        Optional<Path> binDir = prefixDir.map(nodeDir -> SystemInfo.isWindows ? nodeDir : nodeDir.resolve("bin"));
        Optional<Path> nodeModulesDir = prefixDir.flatMap(NodeJsNpmUtils::getGlobalNodeModulesPath);

        return npmExecutable.map(path -> new NodeInstallInfo(
                prefixDir.orElse(null),
                path,
                nodeModulesDir.orElse(null),
                binDir.orElse(null)
        )).orElseGet(NodeInstallInfo::new);
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
