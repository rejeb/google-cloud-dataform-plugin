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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import io.github.rejeb.dataform.language.gcp.common.CommitAuthorConfig;
import io.github.rejeb.dataform.language.gcp.common.DataformApi;
import io.github.rejeb.dataform.language.gcp.common.GcpApiException;
import io.github.rejeb.dataform.language.gcp.workspace.UncommittedChange;
import io.github.rejeb.dataform.language.gcp.workspace.Workspace;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static io.github.rejeb.dataform.language.gcp.workspace.repository.DataformResourceNames.isEmptyRepoException;
import static io.github.rejeb.dataform.language.gcp.workspace.repository.DataformResourceNames.workspaceName;
import static io.github.rejeb.dataform.language.gcp.workspace.repository.WorkspaceFileReader.listAllRepositoryPaths;
import static io.github.rejeb.dataform.language.gcp.workspace.repository.WorkspaceFileReader.listAllWorkspacePaths;
import static io.github.rejeb.dataform.language.gcp.workspace.repository.WorkspaceFileReader.readAllRepositoryFiles;
import static io.github.rejeb.dataform.language.gcp.workspace.repository.WorkspaceFileReader.readAllWorkspaceFiles;
import static io.github.rejeb.dataform.language.gcp.workspace.repository.WorkspaceFileReader.readRepositoryFile;
import static io.github.rejeb.dataform.language.gcp.workspace.repository.WorkspaceFileReader.readWorkspaceFile;

public class GcpDataformWorkspaceRepository implements WorkspaceRepository {

    private static final Logger LOG = Logger.getInstance(GcpDataformWorkspaceRepository.class);

    private static final int PUSH_MAX_RETRIES = 3;
    private static final long PUSH_RETRY_DELAY_MS = 1_000;
    private static final int PUSH_PARALLELISM = 8;
    private static final Executor WRITE_EXECUTOR =
            AppExecutorUtil.createBoundedApplicationPoolExecutor("Dataform workspace push", PUSH_PARALLELISM);

    @Override
    @NotNull
    public List<Workspace> findAll(
            @NotNull String projectId,
            @NotNull String location,
            @NotNull String repositoryId
    ) {
        return DataformApi.call(projectId,
                e -> "Error fetching workspaces from GCP Dataform API. Message: " + e.getMessage(), client -> {
            List<Workspace> result = new ArrayList<>();
            String parent = RepositoryName.of(projectId, location, repositoryId).toString();
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
            @NotNull String projectId,
            @NotNull String location,
            @NotNull String repositoryId,
            @NotNull String workspaceId,
            @NotNull CommitAuthorConfig author
    ) {
        DataformApi.run(projectId, "Error pushing commits to GCP Dataform workspace.", client -> {
            String wsName = workspaceName(projectId, location, repositoryId, workspaceId);
            PushGitCommitsRequest pushRequest = PushGitCommitsRequest.newBuilder()
                    .setName(wsName)
                    .build();
            client.pushGitCommits(pushRequest);
        });
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
        DataformApi.run(projectId, "Error syncing files to GCP Dataform workspace.", client -> {
            String wsName = workspaceName(projectId, location, repositoryId, workspaceId);
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
            CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new)).join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof GcpApiException api) {
                throw api;
            }
            throw new GcpApiException("Error writing files to GCP Dataform workspace.", cause);
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
            @NotNull String projectId,
            @NotNull String location,
            @NotNull String repositoryId,
            @Nullable String workspaceId
    ) {
        try {
            return DataformApi.call(projectId, e -> "Error listing files of GCP Dataform "
                    + (workspaceId != null ? "workspace \"" + workspaceId + "\"" : "repository")
                    + ": " + e.getMessage(), client -> workspaceId != null
                    ? listAllWorkspacePaths(projectId, location, repositoryId, workspaceId, "", client)
                    : listAllRepositoryPaths(projectId, location, repositoryId, "", client));
        } catch (GcpApiException e) {
            if (!isEmptyRepoException(e)) throw e;
            LOG.info("Repository is empty (no commits yet), returning no paths.");
            return List.of();
        }
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
            return DataformApi.call(projectId, "Error reading files from GCP Dataform.", client -> workspaceId != null
                    ? readAllWorkspaceFiles(projectId, location, repositoryId, workspaceId, client)
                    : readAllRepositoryFiles(projectId, location, repositoryId, client));
        } catch (GcpApiException e) {
            if (!isEmptyRepoException(e)) throw e;
            LOG.info("Repository is empty (no commits yet), returning empty file map.");
            return Map.of();
        }
    }

    @Override
    public void createRepository(
            @NotNull String projectId,
            @NotNull String location,
            @NotNull String repositoryId,
            @NotNull String serviceAccount
    ) {
        DataformApi.run(projectId, e -> "Error creating Dataform repository \"" + repositoryId + "\": " + e.getMessage(), client -> {
            String parent = LocationName.of(projectId, location).toString();
            Repository.Builder repository = Repository.newBuilder();
            if (!serviceAccount.isBlank()) {
                repository.setServiceAccount(serviceAccount.trim());
            }
            CreateRepositoryRequest request = CreateRepositoryRequest.newBuilder()
                    .setParent(parent)
                    .setRepositoryId(repositoryId)
                    .setRepository(repository.build())
                    .build();
            client.createRepository(request);
        });
    }

    @Override
    public void createWorkspace(
            @NotNull String projectId,
            @NotNull String location,
            @NotNull String repositoryId,
            @NotNull String workspaceId
    ) {
        DataformApi.run(projectId, e -> "Error creating workspace \"" + workspaceId + "\": " + e.getMessage(), client -> {
            String parent = RepositoryName.of(projectId, location, repositoryId).toString();
            CreateWorkspaceRequest request = CreateWorkspaceRequest.newBuilder()
                    .setParent(parent)
                    .setWorkspaceId(workspaceId)
                    .setWorkspace(com.google.cloud.dataform.v1.Workspace.newBuilder().build())
                    .build();
            client.createWorkspace(request);
        });
    }

    @Override
    @NotNull
    public List<UncommittedChange> fetchFileGitStatuses(
            @NotNull String projectId,
            @NotNull String location,
            @NotNull String repositoryId,
            @NotNull String workspaceId
    ) {
        return DataformApi.call(projectId, "Error fetching git statuses from workspace.", client -> {
            FetchFileGitStatusesRequest request = FetchFileGitStatusesRequest.newBuilder()
                    .setName(workspaceName(projectId, location, repositoryId, workspaceId))
                    .build();
            FetchFileGitStatusesResponse response = client.fetchFileGitStatuses(request);
            return response.getUncommittedFileChangesList().stream()
                    .map(c -> new UncommittedChange(c.getPath(), mapState(c.getState())))
                    .toList();
        });
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
        DataformApi.run(projectId, "Error committing workspace changes.", client -> {
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
        });
    }

    @Override
    public @NotNull String getFileContent(@NotNull String projectId,
                                          @NotNull String location,
                                          @NotNull String repositoryId,
                                          @Nullable String workspaceId,
                                          @NotNull String filePath) {
        try {
            return DataformApi.call(projectId, "Failed to fetch file content", client -> StringUtil.isNotEmpty(workspaceId)
                    ? readWorkspaceFile(projectId, location, repositoryId, workspaceId, filePath, client)
                    : readRepositoryFile(projectId, location, repositoryId, filePath, client));
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