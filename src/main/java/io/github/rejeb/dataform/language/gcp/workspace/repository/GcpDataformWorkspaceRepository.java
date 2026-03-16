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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.concurrency.AppExecutorUtil;
import io.github.rejeb.dataform.language.gcp.common.CommitAuthorConfig;
import io.github.rejeb.dataform.language.gcp.common.GcpApiException;
import io.github.rejeb.dataform.language.gcp.workspace.Workspace;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Function;

public class GcpDataformWorkspaceRepository implements WorkspaceRepository {

    private static final Logger LOG = Logger.getInstance(GcpDataformWorkspaceRepository.class);

    private static final int PUSH_BATCH_SIZE = 10;
    private static final int PUSH_BATCH_DELAY_MS = 300;
    private static final int PUSH_MAX_RETRIES = 3;
    private static final long PUSH_RETRY_DELAY_MS = 1_000;

    // -------------------------------------------------------------------------
    // findAll
    // -------------------------------------------------------------------------

    @Override
    @NotNull
    public List<Workspace> findAll(
            @NotNull String projectId,
            @NotNull String location,
            @NotNull String repositoryId
    ) {
        List<Workspace> result = new ArrayList<>();
        try (DataformClient client = createDataformClient()) {
            String parent = RepositoryName.of(projectId, location, repositoryId).toString();
            ListWorkspacesRequest request = ListWorkspacesRequest.newBuilder()
                    .setParent(parent)
                    .build();
            for (var w : client.listWorkspaces(request).iterateAll()) {
                result.add(Workspace.fromResourceName(w.getName()));
            }
        } catch (IOException e) {
            throw new GcpApiException(
                    "Failed to create DataformClient — check Application Default Credentials.", e);
        } catch (Exception e) {
            throw new GcpApiException("Error fetching workspaces from GCP Dataform API.", e);
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // commit
    // -------------------------------------------------------------------------

    @Override
    public void commit(
            @NotNull String projectId,
            @NotNull String location,
            @NotNull String repositoryId,
            @NotNull String workspaceId,
            @NotNull CommitAuthorConfig author
    ) {
        try (DataformClient client = createDataformClient()) {
            String wsName = workspaceName(projectId, location, repositoryId, workspaceId);

            CommitWorkspaceChangesRequest commitRequest = CommitWorkspaceChangesRequest.newBuilder()
                    .setName(wsName)
                    .setAuthor(CommitAuthor.newBuilder()
                            .setName(author.name())
                            .setEmailAddress(author.emailAddress())
                            .build())
                    .setCommitMessage("Changes from IntelliJ Dataform Plugin")
                    .build();
            client.commitWorkspaceChanges(commitRequest);

            PushGitCommitsRequest pushRequest = PushGitCommitsRequest.newBuilder()
                    .setName(wsName)
                    .build();
            client.pushGitCommits(pushRequest);

        } catch (IOException e) {
            throw new GcpApiException(
                    "Failed to create DataformClient — check Application Default Credentials.", e);
        } catch (Exception e) {
            throw new GcpApiException("Error pushing commits to GCP Dataform workspace.", e);
        }
    }

    // -------------------------------------------------------------------------
    // pull
    // -------------------------------------------------------------------------

    @Override
    public void pull(
            @NotNull String projectId,
            @NotNull String location,
            @NotNull String repositoryId,
            @NotNull String workspaceId,
            @NotNull CommitAuthorConfig author
    ) {
        try (DataformClient client = createDataformClient()) {
            PullGitCommitsRequest request = PullGitCommitsRequest.newBuilder()
                    .setName(workspaceName(projectId, location, repositoryId, workspaceId))
                    .setAuthor(CommitAuthor.newBuilder()
                            .setName(author.name())
                            .setEmailAddress(author.emailAddress())
                            .build())
                    .build();
            client.pullGitCommits(request);
        } catch (IOException e) {
            throw new GcpApiException(
                    "Failed to create DataformClient — check Application Default Credentials.", e);
        } catch (Exception e) {
            throw new GcpApiException("Error pulling commits from GCP Dataform workspace.", e);
        }
    }

    // -------------------------------------------------------------------------
    // push
    // -------------------------------------------------------------------------

    @Override
    public void push(
            @NotNull String projectId,
            @NotNull String location,
            @NotNull String repositoryId,
            @NotNull String workspaceId,
            @NotNull Map<String, String> filesToWrite,
            @NotNull Set<String> pathsToDelete
    ) {
        try (DataformClient client = createDataformClient()) {
            String wsName = workspaceName(projectId, location, repositoryId, workspaceId);

            List<Map.Entry<String, String>> entries = new ArrayList<>(filesToWrite.entrySet());
            List<List<Map.Entry<String, String>>> batches = partition(entries, PUSH_BATCH_SIZE);

            for (List<Map.Entry<String, String>> batch : batches) {
                for (Map.Entry<String, String> entry : batch) {
                    writeFileWithRetry(client, wsName, entry.getKey(), entry.getValue());
                }
                if (batches.indexOf(batch) < batches.size() - 1) {
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

        } catch (IOException e) {
            throw new GcpApiException(
                    "Failed to create DataformClient — check Application Default Credentials.", e);
        } catch (GcpApiException e) {
            throw e;
        } catch (Exception e) {
            throw new GcpApiException("Error syncing files to GCP Dataform workspace.", e);
        }
    }

    // -------------------------------------------------------------------------
    // listAllPaths
    // -------------------------------------------------------------------------

    @Override
    @NotNull
    public Set<String> listAllPaths(
            @NotNull String projectId,
            @NotNull String location,
            @NotNull String repositoryId,
            @NotNull String workspaceId
    ) {
        try (DataformClient client = createDataformClient()) {
            return new HashSet<>(listAllWorkspacePaths(
                    client, projectId, location, repositoryId, workspaceId, ""));
        } catch (IOException e) {
            throw new GcpApiException(
                    "Failed to create DataformClient — check Application Default Credentials.", e);
        }
    }

    // -------------------------------------------------------------------------
    // readFilesFromRepository
    // -------------------------------------------------------------------------

    @Override
    @NotNull
    public Map<String, String> readFilesFromRepository(
            @NotNull String projectId,
            @NotNull String location,
            @NotNull String repositoryId,
            @NotNull List<String> paths
    ) {
        Map<String, String> result = new LinkedHashMap<>();
        try (DataformClient client = createDataformClient()) {
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
        } catch (IOException e) {
            throw new GcpApiException(
                    "Failed to create DataformClient — check Application Default Credentials.", e);
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // readAllFiles
    // -------------------------------------------------------------------------

    @Override
    @NotNull
    public Map<String, String> readAllFiles(
            @NotNull String projectId,
            @NotNull String location,
            @NotNull String repositoryId,
            @Nullable String workspaceId
    ) {
        try (DataformClient client = createDataformClient()) {
            if (workspaceId != null) {
                return readAllWorkspaceFiles(client, projectId, location, repositoryId, workspaceId);
            } else {
                return readAllRepositoryFiles(client, projectId, location, repositoryId);
            }
        } catch (IOException e) {
            throw new GcpApiException(
                    "Failed to create DataformClient — check Application Default Credentials.", e);
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
        try (DataformClient client = createDataformClient()) {
            String parent = LocationName.of(projectId, location).toString();
            CreateRepositoryRequest request = CreateRepositoryRequest.newBuilder()
                    .setParent(parent)
                    .setRepositoryId(repositoryId)
                    .setRepository(Repository.newBuilder().build())
                    .build();
            client.createRepository(request);
        } catch (IOException e) {
            throw new GcpApiException(
                    "Failed to create DataformClient — check Application Default Credentials.", e);
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
        try (DataformClient client = createDataformClient()) {
            String parent = RepositoryName.of(projectId, location, repositoryId).toString();
            CreateWorkspaceRequest request = CreateWorkspaceRequest.newBuilder()
                    .setParent(parent)
                    .setWorkspaceId(workspaceId)
                    .setWorkspace(com.google.cloud.dataform.v1beta1.Workspace.newBuilder().build())
                    .build();
            client.createWorkspace(request);
        } catch (IOException e) {
            throw new GcpApiException(
                    "Failed to create DataformClient — check Application Default Credentials.", e);
        } catch (Exception e) {
            throw new GcpApiException(
                    "Error creating workspace \"" + workspaceId + "\": " + e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Creates a {@link DataformClient} with an extended total timeout to prevent
     * DEADLINE_EXCEEDED errors when pushing many files sequentially.
     */
    private static DataformClient createDataformClient() throws IOException {
        DataformSettings settings = DataformSettings.newBuilder()
                .build();
        return DataformClient.create(settings);
    }

    /**
     * Writes a single file to the workspace with retry on {@link UnavailableException}.
     */
    private static void writeFileWithRetry(
            @NotNull DataformClient client,
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

    /**
     * Splits a list into sub-lists of at most {@code size} elements.
     */
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

    @NotNull
    private Map<String, String> readAllRepositoryFiles(
            @NotNull DataformClient client,
            @NotNull String projectId,
            @NotNull String location,
            @NotNull String repositoryId
    ) {
        List<String> paths = listAllRepositoryPaths(client, projectId, location, repositoryId, "");
        return readFilesInParallel(paths, path ->
                readRepositoryFile(client, projectId, location, repositoryId, path));
    }

    @NotNull
    private Map<String, String> readAllWorkspaceFiles(
            @NotNull DataformClient client,
            @NotNull String projectId,
            @NotNull String location,
            @NotNull String repositoryId,
            @NotNull String workspaceId
    ) {
        List<String> paths = listAllWorkspacePaths(
                client, projectId, location, repositoryId, workspaceId, "");
        return readFilesInParallel(paths, path ->
                readWorkspaceFile(client, projectId, location, repositoryId, workspaceId, path));
    }

    @NotNull
    private static Map<String, String> readFilesInParallel(
            @NotNull List<String> paths,
            @NotNull Function<String, String> reader
    ) {
        if (paths.isEmpty()) return Map.of();

        ExecutorService executor = AppExecutorUtil.getAppExecutorService();

        List<Future<Map.Entry<String, String>>> futures = paths.stream()
                .map(path -> executor.submit(() -> Map.entry(path, reader.apply(path))))
                .toList();

        Map<String, String> result = new LinkedHashMap<>();
        for (Future<Map.Entry<String, String>> future : futures) {
            try {
                Map.Entry<String, String> entry = future.get();
                result.put(entry.getKey(), entry.getValue());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new GcpApiException("File reading interrupted.", e);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof GcpApiException gae) throw gae;
                throw new GcpApiException("Error reading file from GCP Dataform.", cause);
            }
        }
        return result;
    }

    @NotNull
    private String readRepositoryFile(
            @NotNull DataformClient client,
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
    private List<String> listAllRepositoryPaths(
            @NotNull DataformClient client,
            @NotNull String projectId,
            @NotNull String location,
            @NotNull String repositoryId,
            @NotNull String directoryPath
    ) {
        List<String> paths = new ArrayList<>();

        QueryRepositoryDirectoryContentsRequest request =
                QueryRepositoryDirectoryContentsRequest.newBuilder()
                        .setName(repositoryName(projectId, location, repositoryId))
                        .setPath(directoryPath)
                        .build();

        try {
            for (DirectoryEntry entry : client.queryRepositoryDirectoryContents(request).iterateAll()) {
                if (entry.hasFile()) {
                    String fullPath = directoryPath.isEmpty()
                            ? entry.getFile()
                            : directoryPath + "/" + entry.getFile();
                    paths.add(fullPath);
                } else if (entry.hasDirectory()) {
                    String subDir = directoryPath.isEmpty()
                            ? entry.getDirectory()
                            : directoryPath + "/" + entry.getDirectory();
                    paths.addAll(listAllRepositoryPaths(
                            client, projectId, location, repositoryId, subDir));
                }
            }
        } catch (Exception e) {
            if (isEmptyRepoException(e)) {
                LOG.info("Repository \"" + repositoryId + "\" is empty, skipping directory listing.");
                return List.of();
            }
            throw e;
        }
        return paths;
    }

    @NotNull
    private List<String> listAllWorkspacePaths(
            @NotNull DataformClient client,
            @NotNull String projectId,
            @NotNull String location,
            @NotNull String repositoryId,
            @NotNull String workspaceId,
            @NotNull String directoryPath
    ) {
        List<String> paths = new ArrayList<>();

        QueryDirectoryContentsRequest request = QueryDirectoryContentsRequest.newBuilder()
                .setWorkspace(workspaceName(projectId, location, repositoryId, workspaceId))
                .setPath(directoryPath)
                .build();

        for (DirectoryEntry entry : client.queryDirectoryContents(request).iterateAll()) {
            if (entry.hasFile()) {
                paths.add(entry.getFile());
            } else if (entry.hasDirectory()) {
                paths.addAll(listAllWorkspacePaths(
                        client, projectId, location, repositoryId, workspaceId,
                        entry.getDirectory()
                ));
            }
        }
        return paths;
    }

    @NotNull
    private String readWorkspaceFile(
            @NotNull DataformClient client,
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

    /**
     * Builds the fully-qualified GCP Dataform repository resource name.
     * Format: projects/{project}/locations/{location}/repositories/{repository}
     */
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

    /**
     * Returns {@code true} if the exception indicates that the Dataform repository
     * has no commits yet (i.e. was just created and has never been initialized).
     */
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
}
