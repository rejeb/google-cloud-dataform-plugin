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
package io.github.rejeb.dataform.language.gcp.workspace.repository;

import com.google.api.gax.rpc.UnavailableException;
import com.google.cloud.dataform.v1.*;
import com.google.protobuf.ByteString;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import io.github.rejeb.dataform.language.gcp.common.CommitAuthorConfig;
import io.github.rejeb.dataform.language.gcp.common.GcpApiException;
import io.github.rejeb.dataform.language.gcp.workspace.UncommittedChange;
import io.github.rejeb.dataform.language.gcp.workspace.Workspace;
import io.github.rejeb.dataform.language.util.GcpClientsUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class GcpDataformWorkspaceRepository implements WorkspaceRepository, Disposable {

    private static final Logger LOG = Logger.getInstance(GcpDataformWorkspaceRepository.class);

    private static final int PUSH_MAX_RETRIES = 3;
    private static final long PUSH_RETRY_DELAY_MS = 1_000;

    @Override
    public void dispose() {

    }

    @Override
    @NotNull
    public List<Workspace> findAll(
            @NotNull String projectId,
            @NotNull String location,
            @NotNull String repositoryId
    ) {
        List<Workspace> result = new ArrayList<>();
        try (DataformClient client = GcpClientsUtils.dataformClient()) {
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
        try (DataformClient client = GcpClientsUtils.dataformClient()) {
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
        try (DataformClient client = GcpClientsUtils.dataformClient()) {
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
        try (DataformClient client = GcpClientsUtils.dataformClient()) {
            String wsName = workspaceName(projectId, location, repositoryId, workspaceId);
            writeAllFiles(wsName, filesToWrite, client);
            deleteAllFiles(wsName, pathsToDelete, client);
        } catch (GcpApiException e) {
            throw e;
        } catch (Exception e) {
            throw new GcpApiException("Error syncing files to GCP Dataform workspace.", e);
        }
    }

    private void writeAllFiles(@NotNull String wsName,
                               @NotNull Map<String, String> batch, @NotNull DataformClient client) throws Exception {
        batch.entrySet().parallelStream().forEach(entry ->
                writeFileWithRetry(wsName, entry.getKey(), entry.getValue(), client)
        );
    }

    private void deleteAllFiles(@NotNull String wsName,
                                @NotNull Set<String> pathsToDelete, @NotNull DataformClient client) {
        pathsToDelete.forEach(path -> deleteFile(wsName, path, client));
    }

    private void deleteFile(@NotNull String wsName, @NotNull String path, @NotNull DataformClient client) {
        RemoveFileRequest request = RemoveFileRequest.newBuilder()
                .setWorkspace(wsName)
                .setPath(path)
                .build();
        client.removeFile(request);
    }

    private void writeFileWithRetry(@NotNull String wsName,
                                    @NotNull String path,
                                    @NotNull String content,
                                    @NotNull DataformClient client) {
        writeWithRetry(wsName, path, content, 0, client).join();
    }

    @NotNull
    private CompletableFuture<Void> writeWithRetry(@NotNull String wsName,
                                                   @NotNull String path,
                                                   @NotNull String content,
                                                   int attempt,
                                                   @NotNull DataformClient client) {
        return attemptWrite(wsName, path, content, client)
                .exceptionallyCompose(ex -> retryOrFail(wsName, path, content, attempt, ex, client));
    }

    @NotNull
    private CompletableFuture<Void> attemptWrite(@NotNull String wsName,
                                                 @NotNull String path,
                                                 @NotNull String content,
                                                 @NotNull DataformClient client) {
        return CompletableFuture.runAsync(
                () -> doWriteFile(wsName, path, content, client),
                AppExecutorUtil.getAppExecutorService()
        );
    }

    private void doWriteFile(@NotNull String wsName,
                             @NotNull String path,
                             @NotNull String content,
                             @NotNull DataformClient client) {
        WriteFileRequest request = WriteFileRequest.newBuilder()
                .setWorkspace(wsName)
                .setPath(path)
                .setContents(ByteString.copyFromUtf8(content))
                .build();
        client.writeFile(request);
    }

    @NotNull
    private CompletableFuture<Void> retryOrFail(@NotNull String wsName,
                                                @NotNull String path,
                                                @NotNull String content,
                                                int attempt,
                                                @NotNull Throwable ex,
                                                @NotNull DataformClient client) {
        Throwable cause = ex instanceof java.util.concurrent.CompletionException ? ex.getCause() : ex;
        if (!(cause instanceof UnavailableException) || attempt + 1 >= PUSH_MAX_RETRIES) {
            throw new GcpApiException(
                    "Failed to write \"" + path + "\" after " + (attempt + 1) + " attempt(s).", cause);
        }
        long delayMs = PUSH_RETRY_DELAY_MS * (attempt + 1);
        LOG.warn("Retrying write for \"" + path + "\" in " + delayMs + "ms (attempt " + (attempt + 1) + ")");
        CompletableFuture<Void> delayed = new CompletableFuture<>();
        AppExecutorUtil.getAppScheduledExecutorService()
                .schedule(
                        () -> writeWithRetry(wsName, path, content, attempt + 1, client)
                                .whenComplete((v, t) -> {
                                    if (t != null) delayed.completeExceptionally(t);
                                    else delayed.complete(null);
                                }),
                        delayMs,
                        TimeUnit.MILLISECONDS
                );
        return delayed;
    }

    @Override
    @NotNull
    public List<String> listAllPaths(
            @NotNull String projectId,
            @NotNull String location,
            @NotNull String repositoryId,
            @Nullable String workspaceId
    ) {

        try (DataformClient client = GcpClientsUtils.dataformClient()) {
            if (workspaceId != null) {
                return listAllWorkspacePaths(projectId, location, repositoryId, workspaceId, "", client).toList();
            } else {
                return listAllRepositoryPaths(projectId, location, repositoryId, "", client).toList();
            }
        } catch (Exception e) {
            LOG.debug("Error reading files from GCP Dataform.", e);
        }
        return List.of();
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
        try (DataformClient client = GcpClientsUtils.dataformClient()) {
            for (String path : paths) {
                try {
                    ReadRepositoryFileRequest request = ReadRepositoryFileRequest.newBuilder()
                            .setName(repoName)
                            .setPath(path)
                            .build();
                    ReadRepositoryFileResponse response = client.readRepositoryFile(request);
                    result.put(path, response.getContents().toStringUtf8());
                } catch (RuntimeException e) {
                    LOG.warn("Skipping file not found in repository: " + path, e);
                }
            }
            return result;
        } catch (Exception e) {
            LOG.debug("Error reading files from GCP Dataform.", e);
        }
        return Map.of();
    }

    @Override
    @NotNull
    public Map<String, String> readAllFiles(
            @NotNull String projectId,
            @NotNull String location,
            @NotNull String repositoryId,
            @Nullable String workspaceId
    ) {
        try (DataformClient client = GcpClientsUtils.dataformClient()) {
            if (workspaceId != null) {
                return readAllWorkspaceFiles(projectId, location, repositoryId, workspaceId, client);
            } else {
                return readAllRepositoryFiles(projectId, location, repositoryId, client);
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

    @Override
    public void createRepository(
            @NotNull String projectId,
            @NotNull String location,
            @NotNull String repositoryId
    ) {
        try (DataformClient client = GcpClientsUtils.dataformClient()) {
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

    @Override
    public void createWorkspace(
            @NotNull String projectId,
            @NotNull String location,
            @NotNull String repositoryId,
            @NotNull String workspaceId
    ) {
        try (DataformClient client = GcpClientsUtils.dataformClient()) {
            String parent = RepositoryName.of(projectId, location, repositoryId).toString();
            CreateWorkspaceRequest request = CreateWorkspaceRequest.newBuilder()
                    .setParent(parent)
                    .setWorkspaceId(workspaceId)
                    .setWorkspace(com.google.cloud.dataform.v1.Workspace.newBuilder().build())
                    .build();
            client.createWorkspace(request);
        } catch (Exception e) {
            throw new GcpApiException(
                    "Error creating workspace \"" + workspaceId + "\": " + e.getMessage(), e);
        }
    }

    @Override
    @NotNull
    public List<UncommittedChange> fetchFileGitStatuses(
            @NotNull String projectId,
            @NotNull String location,
            @NotNull String repositoryId,
            @NotNull String workspaceId
    ) {
        try (DataformClient client = GcpClientsUtils.dataformClient()) {
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
        try (DataformClient client = GcpClientsUtils.dataformClient()) {
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
        try (DataformClient client = GcpClientsUtils.dataformClient()) {
            if (StringUtil.isNotEmpty(workspaceId)) {
                return readWorkspaceFile(projectId, location, repositoryId, workspaceId, filePath, client);
            } else {
                return readRepositoryFile(projectId, location, repositoryId, filePath, client);
            }
        } catch (Exception e) {
            LOG.warn("Failed to fetch file content", e);
            return "";
        }
    }

    @NotNull
    private Map<String, String> readAllRepositoryFiles(
            @NotNull String projectId,
            @NotNull String location,
            @NotNull String repositoryId,
            @NotNull DataformClient client
    ) {
        Stream<String> paths = listAllRepositoryPaths(projectId, location, repositoryId, "", client);
        return paths.collect(Collectors.toMap(path -> path, path ->
                readRepositoryFile(projectId, location, repositoryId, path, client)));
    }

    @NotNull
    private Map<String, String> readAllWorkspaceFiles(
            @NotNull String projectId,
            @NotNull String location,
            @NotNull String repositoryId,
            @NotNull String workspaceId,
            @NotNull DataformClient client
    ) {
        Stream<String> paths = listAllWorkspacePaths(projectId, location, repositoryId, workspaceId, "", client);
        return paths.collect(Collectors.toMap(path -> path, path ->
                readWorkspaceFile(projectId, location, repositoryId, workspaceId, path, client)));
    }

    @NotNull
    private String readRepositoryFile(
            @NotNull String projectId,
            @NotNull String location,
            @NotNull String repositoryId,
            @NotNull String path,
            @NotNull DataformClient client
    ) {
        ReadRepositoryFileRequest request = ReadRepositoryFileRequest.newBuilder()
                .setName(repositoryName(projectId, location, repositoryId))
                .setPath(path)
                .build();
        ReadRepositoryFileResponse response = client.readRepositoryFile(request);
        return response.getContents().toStringUtf8();
    }

    @NotNull
    private String readWorkspaceFile(
            @NotNull String projectId,
            @NotNull String location,
            @NotNull String repositoryId,
            @NotNull String workspaceId,
            @NotNull String path,
            @NotNull DataformClient client
    ) {
        ReadFileRequest request = ReadFileRequest.newBuilder()
                .setWorkspace(workspaceName(projectId, location, repositoryId, workspaceId))
                .setPath(path)
                .build();
        ReadFileResponse response = client.readFile(request);
        return response.getFileContents().toStringUtf8();
    }

    @NotNull
    private Stream<String> listAllRepositoryPaths(
            @NotNull String projectId,
            @NotNull String location,
            @NotNull String repositoryId,
            @NotNull String directoryPath,
            @NotNull DataformClient client
    ) {
        QueryRepositoryDirectoryContentsRequest request =
                QueryRepositoryDirectoryContentsRequest.newBuilder()
                        .setName(repositoryName(projectId, location, repositoryId))
                        .setPath(directoryPath)
                        .build();
        try {
            DataformClient.QueryRepositoryDirectoryContentsPagedResponse response =
                    client.queryRepositoryDirectoryContents(request);
            return StreamSupport.stream(response.iterateAll().spliterator(), false)
                    .parallel()
                    .flatMap(entry -> resolveRepositoryEntry(
                            entry, projectId, location, repositoryId, directoryPath, client));
        } catch (Exception e) {
            if (isEmptyRepoException(e)) {
                LOG.info("Repository \"" + repositoryId + "\" is empty, skipping directory listing.");
                return Stream.empty();
            }
            throw e;
        }
    }

    @NotNull
    private Stream<String> resolveRepositoryEntry(
            @NotNull DirectoryEntry entry,
            @NotNull String projectId,
            @NotNull String location,
            @NotNull String repositoryId,
            @NotNull String directoryPath,
            @NotNull DataformClient client
    ) {
        if (entry.hasFile()) {
            String fullPath = directoryPath.isEmpty()
                    ? entry.getFile()
                    : directoryPath + "/" + entry.getFile();
            return Stream.of(fullPath);
        }
        if (entry.hasDirectory() && !entry.getDirectory().equals("node_modules")) {
            String subDir = directoryPath.isEmpty()
                    ? entry.getDirectory()
                    : directoryPath + "/" + entry.getDirectory();
            return listAllRepositoryPaths(projectId, location, repositoryId, subDir, client);
        }
        return Stream.empty();
    }

    @NotNull
    private Stream<String> listAllWorkspacePaths(
            @NotNull String projectId,
            @NotNull String location,
            @NotNull String repositoryId,
            @NotNull String workspaceId,
            @NotNull String directoryPath,
            @NotNull DataformClient client
    ) {
        QueryDirectoryContentsRequest request = QueryDirectoryContentsRequest.newBuilder()
                .setWorkspace(workspaceName(projectId, location, repositoryId, workspaceId))
                .setPath(directoryPath)
                .build();
        DataformClient.QueryDirectoryContentsPagedResponse response =
                client.queryDirectoryContents(request);
        return StreamSupport.stream(response.iterateAll().spliterator(), false)
                .parallel()
                .flatMap(entry -> resolveWorkspaceEntry(
                        entry, projectId, location, repositoryId, workspaceId, client));
    }

    @NotNull
    private Stream<String> resolveWorkspaceEntry(
            @NotNull DirectoryEntry entry,
            @NotNull String projectId,
            @NotNull String location,
            @NotNull String repositoryId,
            @NotNull String workspaceId,
            @NotNull DataformClient client
    ) {
        if (entry.hasFile()) return Stream.of(entry.getFile());
        if (entry.hasDirectory() && !entry.getDirectory().equals("node_modules")) {
            return listAllWorkspacePaths(
                    projectId, location, repositoryId, workspaceId, entry.getDirectory(), client);
        }
        return Stream.empty();
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

}