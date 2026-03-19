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
package io.github.rejeb.dataform.language.gcp.workspace.repository;

import com.google.api.gax.rpc.UnavailableException;
import com.google.cloud.dataform.v1beta1.*;
import com.google.protobuf.ByteString;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import io.github.rejeb.dataform.language.gcp.common.CommitAuthorConfig;
import io.github.rejeb.dataform.language.gcp.common.GcpApiException;
import io.github.rejeb.dataform.language.gcp.workspace.UncommittedChange;
import io.github.rejeb.dataform.language.gcp.workspace.Workspace;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class GcpDataformWorkspaceRepository implements WorkspaceRepository, Disposable {

    private static final Logger LOG = Logger.getInstance(GcpDataformWorkspaceRepository.class);

    private static final int PUSH_BATCH_SIZE = 10;
    private static final int PUSH_BATCH_DELAY_MS = 300;
    private static final int PUSH_MAX_RETRIES = 3;
    private static final long PUSH_RETRY_DELAY_MS = 1_000;

    private final DataformClient client;

    public GcpDataformWorkspaceRepository() {
        try {
            this.client = DataformClient.create(DataformSettings.newBuilder().build());
        } catch (IOException e) {
            throw new GcpApiException(
                    "Failed to create DataformClient — check Application Default Credentials.", e);
        }
    }

    @Override
    public void dispose() {
        client.close();
    }

    @Override
    @NotNull
    public List<Workspace> findAll(
            @NotNull String projectId,
            @NotNull String location,
            @NotNull String repositoryId
    ) {
        List<Workspace> result = new ArrayList<>();
        try {
            String parent = RepositoryName.of(projectId, location, repositoryId).toString();
            ListWorkspacesRequest request = ListWorkspacesRequest.newBuilder()
                    .setParent(parent)
                    .build();
            for (var w : client.listWorkspaces(request).iterateAll()) {
                result.add(Workspace.fromResourceName(w.getName()));
            }
        } catch (Exception e) {
            throw new GcpApiException("Error fetching workspaces from GCP Dataform API.", e);
        }
        return result;
    }

    @Override
    public void pushGitCommits(
            @NotNull String projectId,
            @NotNull String location,
            @NotNull String repositoryId,
            @NotNull String workspaceId,
            @NotNull CommitAuthorConfig author
    ) {
        try {
            String wsName = workspaceName(projectId, location, repositoryId, workspaceId);
            PushGitCommitsRequest pushRequest = PushGitCommitsRequest.newBuilder()
                    .setName(wsName)
                    .build();
            client.pushGitCommits(pushRequest);
        } catch (Exception e) {
            throw new GcpApiException("Error pushing commits to GCP Dataform workspace.", e);
        }
    }

    @Override
    public void pull(
            @NotNull String projectId,
            @NotNull String location,
            @NotNull String repositoryId,
            @NotNull String workspaceId,
            @NotNull CommitAuthorConfig author
    ) {
        try {
            PullGitCommitsRequest request = PullGitCommitsRequest.newBuilder()
                    .setName(workspaceName(projectId, location, repositoryId, workspaceId))
                    .setAuthor(CommitAuthor.newBuilder()
                            .setName(author.name())
                            .setEmailAddress(author.emailAddress())
                            .build())
                    .build();
            client.pullGitCommits(request);
        } catch (Exception e) {
            throw new GcpApiException("Error pulling commits from GCP Dataform workspace.", e);
        }
    }

    @Override
    public void push(
            @NotNull String projectId,
            @NotNull String location,
            @NotNull String repositoryId,
            @NotNull String workspaceId,
            @NotNull Map<String, String> filesToWrite,
            @NotNull Set<String> pathsToDelete
    ) {
        try {
            String wsName = workspaceName(projectId, location, repositoryId, workspaceId);
            List<Map.Entry<String, String>> entries = new ArrayList<>(filesToWrite.entrySet());
            List<List<Map.Entry<String, String>>> batches = partition(entries, PUSH_BATCH_SIZE);

            for (int i = 0; i < batches.size(); i++) {
                for (Map.Entry<String, String> entry : batches.get(i)) {
                    writeFileWithRetry(wsName, entry.getKey(), entry.getValue());
                }
                if (i < batches.size() - 1) {
                    sleep(PUSH_BATCH_DELAY_MS);
                }
            }

            for (String path : pathsToDelete) {
                RemoveFileRequest request = RemoveFileRequest.newBuilder()
                        .setWorkspace(wsName)
                        .setPath(path)
                        .build();
                client.removeFile(request);
            }
        } catch (GcpApiException e) {
            throw e;
        } catch (Exception e) {
            throw new GcpApiException("Error syncing files to GCP Dataform workspace.", e);
        }
    }

    @Override
    @NotNull
    public List<String> listAllPaths(
            @NotNull String projectId,
            @NotNull String location,
            @NotNull String repositoryId,
            @Nullable String workspaceId
    ) {
        if (workspaceId != null) {
            return listAllWorkspacePaths(projectId, location, repositoryId, workspaceId, "").toList();
        } else {
            return listAllRepositoryPaths(projectId, location, repositoryId, "").toList();
        }
    }

    @Override
    @NotNull
    public Map<String, String> readFilesFromRepository(
            @NotNull String projectId,
            @NotNull String location,
            @NotNull String repositoryId,
            @NotNull List<String> paths
    ) {
        Map<String, String> result = new LinkedHashMap<>();
        String repoName = RepositoryName.of(projectId, location, repositoryId).toString();
        for (String path : paths) {
            try {
                ReadRepositoryFileRequest request = ReadRepositoryFileRequest.newBuilder()
                        .setName(repoName)
                        .setPath(path)
                        .build();
                ReadRepositoryFileResponse response = client.readRepositoryFile(request);
                result.put(path, response.getContents().toStringUtf8());
            } catch (Exception e) {
                LOG.warn("Skipping file not found in repository: " + path, e);
            }
        }
        return result;
    }


    @Override
    @NotNull
    public Map<String, String> readAllFiles(
            @NotNull String projectId,
            @NotNull String location,
            @NotNull String repositoryId,
            @Nullable String workspaceId
    ) {
        try {
            if (workspaceId != null) {
                return readAllWorkspaceFiles(projectId, location, repositoryId, workspaceId);
            } else {
                return readAllRepositoryFiles(projectId, location, repositoryId);
            }
        } catch (GcpApiException e) {
            if (isEmptyRepoException(e)) {
                LOG.info("Repository is empty (no commits yet), returning empty file map.");
                return Map.of();
            }
            throw e;
        } catch (Exception e) {
            if (isEmptyRepoException(e)) {
                LOG.info("Repository is empty (no commits yet), returning empty file map.");
                return Map.of();
            }
            throw new GcpApiException("Error reading files from GCP Dataform.", e);
        }
    }

    // -------------------------------------------------------------------------
    // createRepository
    // -------------------------------------------------------------------------

    @Override
    public void createRepository(
            @NotNull String projectId,
            @NotNull String location,
            @NotNull String repositoryId
    ) {
        try {
            String parent = LocationName.of(projectId, location).toString();
            CreateRepositoryRequest request = CreateRepositoryRequest.newBuilder()
                    .setParent(parent)
                    .setRepositoryId(repositoryId)
                    .setRepository(Repository.newBuilder().build())
                    .build();
            client.createRepository(request);
        } catch (Exception e) {
            throw new GcpApiException(
                    "Error creating Dataform repository \"" + repositoryId + "\": " + e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // createWorkspace
    // -------------------------------------------------------------------------

    @Override
    public void createWorkspace(
            @NotNull String projectId,
            @NotNull String location,
            @NotNull String repositoryId,
            @NotNull String workspaceId
    ) {
        try {
            String parent = RepositoryName.of(projectId, location, repositoryId).toString();
            CreateWorkspaceRequest request = CreateWorkspaceRequest.newBuilder()
                    .setParent(parent)
                    .setWorkspaceId(workspaceId)
                    .setWorkspace(com.google.cloud.dataform.v1beta1.Workspace.newBuilder().build())
                    .build();
            client.createWorkspace(request);
        } catch (Exception e) {
            throw new GcpApiException(
                    "Error creating workspace \"" + workspaceId + "\": " + e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // fetchFileGitStatuses
    // -------------------------------------------------------------------------

    @Override
    @NotNull
    public List<UncommittedChange> fetchFileGitStatuses(
            @NotNull String projectId,
            @NotNull String location,
            @NotNull String repositoryId,
            @NotNull String workspaceId
    ) {
        try {
            FetchFileGitStatusesRequest request = FetchFileGitStatusesRequest.newBuilder()
                    .setName(workspaceName(projectId, location, repositoryId, workspaceId))
                    .build();
            FetchFileGitStatusesResponse response = client.fetchFileGitStatuses(request);
            return response.getUncommittedFileChangesList().stream()
                    .map(c -> new UncommittedChange(c.getPath(), mapState(c.getState())))
                    .toList();
        } catch (Exception e) {
            throw new GcpApiException("Error fetching git statuses from workspace.", e);
        }
    }

    // -------------------------------------------------------------------------
    // commitWorkspaceChanges
    // -------------------------------------------------------------------------

    @Override
    public void commitWorkspaceChanges(
            @NotNull String projectId,
            @NotNull String location,
            @NotNull String repositoryId,
            @NotNull String workspaceId,
            @NotNull List<String> paths,
            @NotNull String message,
            @NotNull CommitAuthorConfig author
    ) {
        try {
            CommitWorkspaceChangesRequest request = CommitWorkspaceChangesRequest.newBuilder()
                    .setName(workspaceName(projectId, location, repositoryId, workspaceId))
                    .setAuthor(CommitAuthor.newBuilder()
                            .setName(author.name())
                            .setEmailAddress(author.emailAddress())
                            .build())
                    .setCommitMessage(message)
                    .addAllPaths(paths)
                    .build();
            client.commitWorkspaceChanges(request);
        } catch (Exception e) {
            throw new GcpApiException("Error committing workspace changes.", e);
        }
    }

    @Override
    public @NotNull String getFileContent(@NotNull String projectId,
                                          @NotNull String location,
                                          @NotNull String repositoryId,
                                          @Nullable String workspaceId,
                                          @NotNull String filePath) {
        try {
            if (StringUtil.isNotEmpty(workspaceId)) {
                return readWorkspaceFile(projectId, location, repositoryId, workspaceId, filePath);
            } else {
                return readRepositoryFile(projectId, location, repositoryId, filePath);
            }
        } catch (Exception e) {
            LOG.warn("Failed to fetch file content", e);
            return "";
        }
    }

    private void writeFileWithRetry(
            @NotNull String wsName,
            @NotNull String path,
            @NotNull String content
    ) {
        int attempt = 0;
        while (true) {
            try {
                WriteFileRequest request = WriteFileRequest.newBuilder()
                        .setWorkspace(wsName)
                        .setPath(path)
                        .setContents(ByteString.copyFromUtf8(content))
                        .build();
                client.writeFile(request);
                return;
            } catch (UnavailableException e) {
                attempt++;
                if (attempt >= PUSH_MAX_RETRIES) {
                    throw new GcpApiException(
                            "Failed to write file \"" + path + "\" after " + attempt + " attempts.", e);
                }
                sleep(PUSH_RETRY_DELAY_MS * attempt);
            }
        }
    }

    @NotNull
    private Map<String, String> readAllRepositoryFiles(
            @NotNull String projectId,
            @NotNull String location,
            @NotNull String repositoryId
    ) {
        Stream<String> paths = listAllRepositoryPaths(projectId, location, repositoryId, "");
        return paths.collect(Collectors.toMap(path -> path, path ->
                readRepositoryFile(projectId, location, repositoryId, path)));
    }

    @NotNull
    private Map<String, String> readAllWorkspaceFiles(
            @NotNull String projectId,
            @NotNull String location,
            @NotNull String repositoryId,
            @NotNull String workspaceId
    ) {
        Stream<String> paths = listAllWorkspacePaths(projectId, location, repositoryId, workspaceId, "");
        return paths.collect(Collectors.toMap(path -> path, path ->
                readWorkspaceFile(projectId, location, repositoryId, workspaceId, path)));
    }

    @NotNull
    private String readRepositoryFile(
            @NotNull String projectId,
            @NotNull String location,
            @NotNull String repositoryId,
            @NotNull String path
    ) {
        ReadRepositoryFileRequest request = ReadRepositoryFileRequest.newBuilder()
                .setName(repositoryName(projectId, location, repositoryId))
                .setPath(path)
                .build();
        ReadRepositoryFileResponse response = client.readRepositoryFile(request);
        return response.getContents().toStringUtf8();
    }

    @NotNull
    private Stream<String> listAllRepositoryPaths(
            @NotNull String projectId,
            @NotNull String location,
            @NotNull String repositoryId,
            @NotNull String directoryPath
    ) {
        QueryRepositoryDirectoryContentsRequest request =
                QueryRepositoryDirectoryContentsRequest.newBuilder()
                        .setName(repositoryName(projectId, location, repositoryId))
                        .setPath(directoryPath)
                        .build();
        try {
            DataformClient.QueryRepositoryDirectoryContentsPagedResponse response = client.queryRepositoryDirectoryContents(request);
            return StreamSupport.stream(response.iterateAll().spliterator(), false)
                    .parallel()
                    .flatMap(entry -> {
                        if (entry.hasFile()) {
                            String fullPath = directoryPath.isEmpty()
                                    ? entry.getFile()
                                    : directoryPath + "/" + entry.getFile();
                            return Stream.of(fullPath);
                        } else if (entry.hasDirectory() && !entry.getDirectory().equals("node_modules")) {
                            String subDir = directoryPath.isEmpty()
                                    ? entry.getDirectory()
                                    : directoryPath + "/" + entry.getDirectory();
                            return listAllRepositoryPaths(projectId, location, repositoryId, subDir);
                        } else {
                            return Stream.empty();
                        }
                    });
        } catch (Exception e) {
            if (isEmptyRepoException(e)) {
                LOG.info("Repository \"" + repositoryId + "\" is empty, skipping directory listing.");
                return Stream.empty();
            }
            throw e;
        }
    }

    @NotNull
    private Stream<String> listAllWorkspacePaths(
            @NotNull String projectId,
            @NotNull String location,
            @NotNull String repositoryId,
            @NotNull String workspaceId,
            @NotNull String directoryPath
    ) {
        QueryDirectoryContentsRequest request = QueryDirectoryContentsRequest.newBuilder()
                .setWorkspace(workspaceName(projectId, location, repositoryId, workspaceId))
                .setPath(directoryPath)
                .build();
        DataformClient.QueryDirectoryContentsPagedResponse response = client.queryDirectoryContents(request);
        return StreamSupport.stream(response.iterateAll().spliterator(), false)
                .parallel()
                .flatMap(entry -> {
                    if (entry.hasFile()) {
                        return Stream.of(entry.getFile());
                    } else if (entry.hasDirectory() && !entry.getDirectory().equals("node_modules")) {
                        return listAllWorkspacePaths(
                                projectId, location, repositoryId, workspaceId, entry.getDirectory());
                    } else {
                        return Stream.empty();
                    }
                });
    }

    @NotNull
    private String readWorkspaceFile(
            @NotNull String projectId,
            @NotNull String location,
            @NotNull String repositoryId,
            @NotNull String workspaceId,
            @NotNull String path
    ) {
        ReadFileRequest request = ReadFileRequest.newBuilder()
                .setWorkspace(workspaceName(projectId, location, repositoryId, workspaceId))
                .setPath(path)
                .build();
        ReadFileResponse response = client.readFile(request);
        return response.getFileContents().toStringUtf8();
    }

    private static String workspaceName(
            @NotNull String projectId,
            @NotNull String location,
            @NotNull String repositoryId,
            @NotNull String workspaceId
    ) {
        return WorkspaceName.of(projectId, location, repositoryId, workspaceId).toString();
    }

    @NotNull
    private static String repositoryName(
            @NotNull String projectId,
            @NotNull String location,
            @NotNull String repositoryId
    ) {
        return "projects/" + projectId
                + "/locations/" + location
                + "/repositories/" + repositoryId;
    }

    private static boolean isEmptyRepoException(@NotNull Throwable t) {
        Throwable current = t;
        while (current != null) {
            String msg = current.getMessage();
            if (msg != null && msg.contains("Reading from empty repo")) return true;
            if (current instanceof com.google.api.gax.rpc.FailedPreconditionException) return true;
            current = current.getCause();
        }
        return false;
    }

    private static UncommittedChange.ChangeState mapState(
            @NotNull FetchFileGitStatusesResponse.UncommittedFileChange.State state
    ) {
        return switch (state) {
            case ADDED -> UncommittedChange.ChangeState.ADDED;
            case DELETED -> UncommittedChange.ChangeState.DELETED;
            case MODIFIED -> UncommittedChange.ChangeState.MODIFIED;
            case HAS_CONFLICTS -> UncommittedChange.ChangeState.HAS_CONFLICTS;
            default -> UncommittedChange.ChangeState.UNKNOWN;
        };
    }

    @NotNull
    private static <T> List<List<T>> partition(@NotNull List<T> list, int size) {
        List<List<T>> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            result.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return result;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
