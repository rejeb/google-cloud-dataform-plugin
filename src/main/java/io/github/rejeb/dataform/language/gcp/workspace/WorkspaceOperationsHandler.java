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
package io.github.rejeb.dataform.language.gcp.workspace;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import io.github.rejeb.dataform.language.gcp.common.CommitAuthorConfig;
import io.github.rejeb.dataform.language.gcp.common.GcpApiException;
import io.github.rejeb.dataform.language.gcp.common.GcpConfigProvider.RepositoryCoordinates;
import io.github.rejeb.dataform.language.gcp.common.GcpConfigProvider;
import io.github.rejeb.dataform.language.gcp.settings.DataformRepositoryConfig;
import io.github.rejeb.dataform.language.gcp.workspace.repository.WorkspaceRepository;
import io.github.rejeb.dataform.language.util.DataformPaths;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

public class WorkspaceOperationsHandler implements WorkspaceOperations {

    private static final Logger LOG = Logger.getInstance(WorkspaceOperationsHandler.class);

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
        RepositoryCoordinates config = readConfig();
        if (config == null) return List.of();
        return workspaceRepository.findAll(config.projectId(), config.location(), config.repositoryId());
    }

    @Override
    public void pushGitCommits(@NotNull String workspaceId) {
        RepositoryCoordinates config = readConfig();
        if (config == null) return;
        CommitAuthorConfig author = configProvider.getCommitAuthor();
        workspaceRepository.pushGitCommits(config.projectId(), config.location(), config.repositoryId(),
                workspaceId, author);
    }

    @Override
    @NotNull
    public Map<String, String> fetchCode(@Nullable String workspaceId) {
        RepositoryCoordinates config = readConfig();
        if (config == null) return Map.of();
        return workspaceRepository.readAllFiles(
                config.projectId(), config.location(), config.repositoryId(), workspaceId);
    }

    @Override
    public void pullCode(@Nullable String workspaceId) {
        RepositoryCoordinates config = readConfig();
        if (config == null) return;
        Map<String, String> files = workspaceRepository.readAllFiles(
                config.projectId(), config.location(), config.repositoryId(), workspaceId);
        if (!files.isEmpty()) {
            writeFilesToVfs(files);
        }
    }

    @Override
    public void testConnection(@NotNull DataformRepositoryConfig config) {
        workspaceRepository.findAll(config.projectId(), config.location(), config.repositoryId());
    }

    @Override
    public void pushCode(@NotNull String workspaceId, @NotNull Predicate<Set<String>> deletionApproval) {
        RepositoryCoordinates config = readConfig();
        if (config == null) return;
        saveDocuments();
        Map<String, String> localFiles = ReadAction.computeBlocking(() -> {
            List<String> paths = filesResolver.apply(project);
            VirtualFile[] roots = ProjectRootManager.getInstance(project).getContentRoots();
            if (roots.length == 0 || paths.isEmpty()) return Map.of();

            VirtualFile contentRoot = roots[0];
            Map<String, String> result = new LinkedHashMap<>();
            for (String path : paths) {
                VirtualFile vf = contentRoot.findFileByRelativePath(path);
                if (vf != null && !vf.isDirectory()) {
                    try {
                        result.put(path, new String(vf.contentsToByteArray(), StandardCharsets.UTF_8));
                    } catch (IOException e) {
                        LOG.warn("Cannot read local file: " + path, e);
                    }
                }
            }
            return result;
        });

        if (localFiles.isEmpty()) {
            LOG.info("pushCode: no local files resolved, skipping push for workspace: " + workspaceId);
            return;
        }

        List<String> remotePaths = workspaceRepository.listAllPaths(
                config.projectId(), config.location(), config.repositoryId(), workspaceId);

        Set<String> toDelete = new HashSet<>(remotePaths);
        toDelete.removeAll(localFiles.keySet());
        if (!toDelete.isEmpty() && !deletionApproval.test(Collections.unmodifiableSet(toDelete))) {
            LOG.info("pushCode: deletion of " + toDelete.size() + " remote file(s) declined, push cancelled");
            return;
        }
        workspaceRepository.push(
                config.projectId(), config.location(), config.repositoryId(),
                workspaceId, localFiles, toDelete);
    }

    /**
     * Writes the unsaved editors to disk, so that what is pushed is what the user sees. The local
     * contents are read from the files, not from the documents.
     */
    private static void saveDocuments() {
        ApplicationManager.getApplication().invokeAndWait(
                () -> FileDocumentManager.getInstance().saveAllDocuments());
    }

    public void createRepository(@NotNull DataformRepositoryConfig config) {
        workspaceRepository.createRepository(
                config.projectId(),
                config.location(),
                config.repositoryId(),
                config.serviceAccount()
        );
    }

    /**
     * Creates a new workspace in the active GCP Dataform repository.
     *
     * @param workspaceId the ID of the workspace to create
     * @throws GcpApiException if creation fails or config is missing
     */
    public void createWorkspace(@NotNull String workspaceId) {
        RepositoryCoordinates config = readConfig();
        if (config == null) {
            throw new GcpApiException(
                    "No active repository config — configure a repository first.");
        }
        workspaceRepository.createWorkspace(
                config.projectId(),
                config.location(),
                config.repositoryId(),
                workspaceId
        );
    }

    @Override
    @NotNull
    public List<UncommittedChange> fetchGitStatuses(@NotNull String workspaceId) {
        RepositoryCoordinates config = readConfig();
        if (config == null) return List.of();
        return workspaceRepository.fetchFileGitStatuses(
                config.projectId(), config.location(), config.repositoryId(), workspaceId);
    }

    @Override
    public void commitWorkspaceChanges(
            @NotNull String workspaceId,
            @NotNull List<String> paths,
            @NotNull String message
    ) {
        RepositoryCoordinates config = readConfig();
        if (config == null) return;
        CommitAuthorConfig author = configProvider.getCommitAuthor();
        workspaceRepository.commitWorkspaceChanges(
                config.projectId(), config.location(), config.repositoryId(),
                workspaceId, paths, message, author);
    }

    @Override
    public List<String> listAllPaths(@Nullable String workspaceId) {
        RepositoryCoordinates config = readConfig();
        return config == null ? List.of() : workspaceRepository.listAllPaths(
                config.projectId(), config.location(), config.repositoryId(), workspaceId);
    }

    @Override
    public @NotNull String getFileContent(@Nullable String workspaceId, @NotNull String filePath) {
        RepositoryCoordinates config = readConfig();
        return config == null ? "" : workspaceRepository.getFileContent(
                config.projectId(), config.location(), config.repositoryId(), workspaceId, filePath);
    }

    @Nullable
    private RepositoryCoordinates readConfig() {
        return GcpConfigProvider.coordinatesOf(configProvider);
    }

    private void writeFilesToVfs(@NotNull Map<String, String> files) {
        VirtualFile[] roots = ProjectRootManager.getInstance(project).getContentRoots();
        if (roots.length == 0) return;
        VirtualFile contentRoot = roots[0];

        try {
            WriteAction.runAndWait(() -> {
                for (Map.Entry<String, String> entry : files.entrySet()) {
                    DataformPaths.writeText(contentRoot, entry.getKey(), entry.getValue());
                }
            });
        } catch (IOException e) {
            throw new GcpApiException("Failed to write pulled files to local project.", e);
        }
    }
}
