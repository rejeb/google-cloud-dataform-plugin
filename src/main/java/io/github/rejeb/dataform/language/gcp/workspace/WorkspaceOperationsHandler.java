/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.rejeb.dataform.language.gcp.workspace;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import io.github.rejeb.dataform.language.gcp.common.CommitAuthorConfig;
import io.github.rejeb.dataform.language.gcp.common.GcpApiException;
import io.github.rejeb.dataform.language.gcp.common.GcpConfigProvider;
import io.github.rejeb.dataform.language.gcp.settings.DataformRepositoryConfig;
import io.github.rejeb.dataform.language.gcp.workspace.repository.WorkspaceRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class WorkspaceOperationsHandler implements WorkspaceOperations {

    private final WorkspaceRepository workspaceRepository;
    private final GcpConfigProvider configProvider;
    private final Function<Project, List<String>> filesResolver;
    private final Project project;

    public WorkspaceOperationsHandler(
            @NotNull WorkspaceRepository workspaceRepository,
            @NotNull GcpConfigProvider configProvider,
            @NotNull Project project
    ) {
        this(workspaceRepository, configProvider, project, DataformProjectFilesResolver::resolve);
    }

    WorkspaceOperationsHandler(
            @NotNull WorkspaceRepository workspaceRepository,
            @NotNull GcpConfigProvider configProvider,
            @NotNull Project project,
            @NotNull Function<Project, List<String>> filesResolver
    ) {
        this.workspaceRepository = workspaceRepository;
        this.configProvider = configProvider;
        this.project = project;
        this.filesResolver = filesResolver;
    }

    @Override
    @NotNull
    public List<Workspace> listWorkspaces() {
        GcpConfig config = readConfig();
        if (config == null) return List.of();

        return workspaceRepository.findAll(config.projectId, config.location, config.repositoryId);
    }

    @Override
    public void pushCode(@NotNull String workspaceId) {
        GcpConfig config = readConfig();
        if (config == null) return;

        List<String> paths = ReadAction.compute(() -> filesResolver.apply(project));
        if (paths.isEmpty()) return;

        workspaceRepository.push(config.projectId, config.location, config.repositoryId, workspaceId);
    }

    @Override
    @NotNull
    public Map<String, String> pullCode(@Nullable String workspaceId) {
        GcpConfig config = readConfig();
        if (config == null) return Map.of();

        return workspaceRepository.readAllFiles(
                config.projectId, config.location, config.repositoryId, workspaceId);
    }

    @Override
    public void syncCode(@Nullable String workspaceId) {
        GcpConfig config = readConfig();
        if (config == null) return;

        Map<String, String> files = workspaceRepository.readAllFiles(
                config.projectId, config.location, config.repositoryId, workspaceId);

        if (!files.isEmpty()) {
            writeFilesToVfs(files);
        }
    }

    @Override
    public void testConnection(@NotNull DataformRepositoryConfig config) {
        workspaceRepository.findAll(config.projectId(), config.location(), config.repositoryId());
    }

    /**
     * Reads the GCP config under a read action since providers may access PSI.
     *
     * @return resolved config, or {@code null} if any field is missing
     */
    @Nullable
    private GcpConfig readConfig() {
        return ReadAction.compute(() -> {
            String projectId = configProvider.getProjectId();
            String location = configProvider.getLocation();
            String repositoryId = configProvider.getRepositoryId();
            if (projectId == null || location == null || repositoryId == null) {
                return null;
            }
            return new GcpConfig(projectId, location, repositoryId);
        });
    }

    private void writeFilesToVfs(@NotNull Map<String, String> files) {
        VirtualFile[] roots = ProjectRootManager.getInstance(project).getContentRoots();
        if (roots.length == 0) return;
        VirtualFile contentRoot = roots[0];

        try {
            WriteAction.runAndWait(() -> {
                for (Map.Entry<String, String> entry : files.entrySet()) {
                    writeFile(contentRoot, entry.getKey(), entry.getValue());
                }
            });
        } catch (IOException e) {
            throw new GcpApiException("Failed to write pulled files to local project.", e);
        }
    }

    private static void writeFile(
            @NotNull VirtualFile contentRoot,
            @NotNull String relativePath,
            @NotNull String content
    ) throws IOException {
        String[] segments = relativePath.split("/");
        VirtualFile dir = contentRoot;

        for (int i = 0; i < segments.length - 1; i++) {
            VirtualFile child = dir.findChild(segments[i]);
            dir = child != null ? child : dir.createChildDirectory(null, segments[i]);
        }

        String fileName = segments[segments.length - 1];
        VirtualFile file = dir.findChild(fileName);
        if (file == null) {
            file = dir.createChildData(null, fileName);
        }
        file.setBinaryContent(content.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Immutable snapshot of the GCP config read under a read action.
     */
    private record GcpConfig(
            @NotNull String projectId,
            @NotNull String location,
            @NotNull String repositoryId
    ) {}
}
