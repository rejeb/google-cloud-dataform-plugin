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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.concurrency.AppExecutorUtil;
import io.github.rejeb.dataform.language.gcp.common.CommitAuthorConfig;
import io.github.rejeb.dataform.language.gcp.common.DataformApi;
import io.github.rejeb.dataform.language.gcp.common.GcpApiException;
import io.github.rejeb.dataform.language.gcp.common.RepositoryCoordinates;
import io.github.rejeb.dataform.language.gcp.workspace.DataformRepository;
import io.github.rejeb.dataform.language.gcp.workspace.UncommittedChange;
import io.github.rejeb.dataform.language.gcp.workspace.Workspace;
import io.github.rejeb.dataform.language.util.concurrent.Futures;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static io.github.rejeb.dataform.language.gcp.workspace.repository.DataformResourceNames.isEmptyRepoException;

public class GcpDataformWorkspaceRepository implements WorkspaceRepository {

    private static final Logger LOG = Logger.getInstance(GcpDataformWorkspaceRepository.class);

    private static final int PUSH_MAX_RETRIES = 3;
    private static final long PUSH_RETRY_DELAY_MS = 1_000;
    private static final int PUSH_PARALLELISM = 8;
    private static final Executor WRITE_EXECUTOR =
            AppExecutorUtil.createBoundedApplicationPoolExecutor("Dataform workspace push", PUSH_PARALLELISM);

    @Override
    @NotNull
    public DataformRepository findRepository(
            @NotNull RepositoryCoordinates repository
    ) {
        return DataformApi.call(repository.projectId(),
                e -> "Error reading GCP Dataform repository \"" + repository.repositoryId() + "\": " + e.getMessage(),
                client -> DataformRepositoryMapper.toDataformRepository(
                        client.getRepository(repository.repositoryName())));
    }

    @Override
    @NotNull
    public List<DataformRepository> findRepositories(@NotNull String projectId, @NotNull String location) {
        return DataformApi.call(projectId,
                e -> "Error listing Dataform repositories: " + e.getMessage(), client -> {
            ListRepositoriesRequest request = ListRepositoriesRequest.newBuilder()
                    .setParent(LocationName.of(projectId, location).toString())
                    .build();
            return DataformRepositoryMapper.toDataformRepositories(client.listRepositories(request).iterateAll());
        });
    }

    @Override
    @NotNull
    public List<Workspace> findAll(
            @NotNull RepositoryCoordinates repository
    ) {
        return DataformApi.call(repository.projectId(),
                e -> "Error fetching workspaces from GCP Dataform API. Message: " + e.getMessage(), client -> {
            List<Workspace> result = new ArrayList<>();
            String parent = repository.repositoryName();
            ListWorkspacesRequest request = ListWorkspacesRequest.newBuilder()
                    .setParent(parent)
                    .build();
            for (var w : client.listWorkspaces(request).iterateAll()) {
                result.add(Workspace.fromResourceName(w.getName()));
            }
            return result;
        });
    }

    @Override
    public void pushGitCommits(
            @NotNull RepositoryCoordinates repository,
            @NotNull String workspaceId,
            @Nullable String remoteBranch,
            @NotNull CommitAuthorConfig author
    ) {
        DataformApi.run(repository.projectId(), "Error pushing commits to GCP Dataform workspace.", client ->
                client.pushGitCommits(pushRequest(repository.workspaceName(workspaceId), remoteBranch)));
    }

    static @NotNull PushGitCommitsRequest pushRequest(@NotNull String workspaceName, @Nullable String remoteBranch) {
        PushGitCommitsRequest.Builder request = PushGitCommitsRequest.newBuilder().setName(workspaceName);
        if (remoteBranch != null) {
            request.setRemoteBranch(remoteBranch);
        }
        return request.build();
    }

    @Override
    public void push(
            @NotNull RepositoryCoordinates repository,
            @NotNull String workspaceId,
            @NotNull Map<String, String> filesToWrite,
            @NotNull Set<String> pathsToDelete
    ) {
        DataformApi.run(repository.projectId(), "Error syncing files to GCP Dataform workspace.", client -> {
            String wsName = repository.workspaceName(workspaceId);
            writeAllFiles(wsName, filesToWrite, client);
            deleteAllFiles(wsName, pathsToDelete, client);
        });
    }

    private void writeAllFiles(@NotNull String wsName,
                               @NotNull Map<String, String> batch, @NotNull DataformClient client) {
        List<CompletableFuture<Void>> writes = new ArrayList<>(batch.size());
        for (Map.Entry<String, String> entry : batch.entrySet()) {
            writes.add(writeWithRetry(wsName, entry.getKey(), entry.getValue(), 0, client));
        }
        try {
            Futures.await(CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new)));
        } catch (GcpApiException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new GcpApiException("Error writing files to GCP Dataform workspace.", e);
        }
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
                WRITE_EXECUTOR
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
        Throwable cause = ex instanceof CompletionException ? ex.getCause() : ex;
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
            @NotNull RepositoryCoordinates repository,
            @Nullable String workspaceId
    ) {
        try {
            return DataformApi.call(repository.projectId(), e -> "Error listing files of GCP Dataform "
                    + (Workspace.isMainBranch(workspaceId) ? "repository" : "workspace \"" + workspaceId + "\"")
                    + ": " + e.getMessage(),
                    client -> WorkspaceFileReader.listAll(FileSource.of(repository, workspaceId, client)));
        } catch (GcpApiException e) {
            if (!isEmptyRepoException(e)) throw e;
            LOG.info("Repository is empty (no commits yet), returning no paths.");
            return List.of();
        }
    }

    @Override
    @NotNull
    public Map<String, String> readAllFiles(
            @NotNull RepositoryCoordinates repository,
            @Nullable String workspaceId
    ) {
        try {
            return DataformApi.call(repository.projectId(), "Error reading files from GCP Dataform.",
                    client -> WorkspaceFileReader.readAll(FileSource.of(repository, workspaceId, client)));
        } catch (GcpApiException e) {
            if (!isEmptyRepoException(e)) throw e;
            LOG.info("Repository is empty (no commits yet), returning empty file map.");
            return Map.of();
        }
    }

    @Override
    public void createRepository(
            @NotNull RepositoryCoordinates repository,
            @NotNull String serviceAccount
    ) {
        DataformApi.run(repository.projectId(), e -> "Error creating Dataform repository \"" + repository.repositoryId() + "\": " + e.getMessage(), client -> {
            String parent = LocationName.of(repository.projectId(), repository.location()).toString();
            Repository.Builder definition = Repository.newBuilder();
            if (!serviceAccount.isBlank()) {
                definition.setServiceAccount(serviceAccount.trim());
            }
            CreateRepositoryRequest request = CreateRepositoryRequest.newBuilder()
                    .setParent(parent)
                    .setRepositoryId(repository.repositoryId())
                    .setRepository(definition.build())
                    .build();
            client.createRepository(request);
        });
    }

    @Override
    public void createWorkspace(
            @NotNull RepositoryCoordinates repository,
            @NotNull String workspaceId
    ) {
        DataformApi.run(repository.projectId(), e -> "Error creating workspace \"" + workspaceId + "\": " + e.getMessage(), client -> {
            String parent = repository.repositoryName();
            CreateWorkspaceRequest request = CreateWorkspaceRequest.newBuilder()
                    .setParent(parent)
                    .setWorkspaceId(workspaceId)
                    .setWorkspace(com.google.cloud.dataform.v1.Workspace.newBuilder().build())
                    .build();
            client.createWorkspace(request);
        });
    }

    @Override
    public void deleteWorkspace(
            @NotNull RepositoryCoordinates repository,
            @NotNull String workspaceId
    ) {
        DataformApi.run(repository.projectId(), e -> "Error deleting workspace \"" + workspaceId + "\": " + e.getMessage(), client ->
                client.deleteWorkspace(repository.workspaceName(workspaceId)));
    }

    @Override
    @NotNull
    public List<UncommittedChange> fetchFileGitStatuses(
            @NotNull RepositoryCoordinates repository,
            @NotNull String workspaceId
    ) {
        return DataformApi.call(repository.projectId(), "Error fetching git statuses from workspace.", client -> {
            FetchFileGitStatusesRequest request = FetchFileGitStatusesRequest.newBuilder()
                    .setName(repository.workspaceName(workspaceId))
                    .build();
            FetchFileGitStatusesResponse response = client.fetchFileGitStatuses(request);
            return response.getUncommittedFileChangesList().stream()
                    .map(c -> new UncommittedChange(c.getPath(), mapState(c.getState())))
                    .toList();
        });
    }

    @Override
    public void commitWorkspaceChanges(
            @NotNull RepositoryCoordinates repository,
            @NotNull String workspaceId,
            @NotNull List<String> paths,
            @NotNull String message,
            @NotNull CommitAuthorConfig author
    ) {
        DataformApi.run(repository.projectId(), "Error committing workspace changes.", client -> {
            CommitWorkspaceChangesRequest request = CommitWorkspaceChangesRequest.newBuilder()
                    .setName(repository.workspaceName(workspaceId))
                    .setAuthor(CommitAuthor.newBuilder()
                            .setName(author.name())
                            .setEmailAddress(author.emailAddress())
                            .build())
                    .setCommitMessage(message)
                    .addAllPaths(paths)
                    .build();
            client.commitWorkspaceChanges(request);
        });
    }

    @Override
    public @NotNull String getFileContent(@NotNull RepositoryCoordinates repository,
                                          @Nullable String workspaceId,
                                          @NotNull String filePath) {
        try {
            return DataformApi.call(repository.projectId(), "Failed to fetch file content",
                    client -> FileSource.of(repository, workspaceId, client).read(filePath));
        } catch (GcpApiException e) {
            LOG.warn("Failed to fetch file content", e);
            return "";
        }
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