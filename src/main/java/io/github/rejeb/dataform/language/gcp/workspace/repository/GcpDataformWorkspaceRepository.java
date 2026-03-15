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

    @Override
    @NotNull
    public List<Workspace> findAll(
            @NotNull String projectId,
            @NotNull String location,
            @NotNull String repositoryId
    ) {
        List<Workspace> result = new ArrayList<>();
        try (DataformClient client = DataformClient.create()) {
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

    @Override
    public void commit(
            @NotNull String projectId,
            @NotNull String location,
            @NotNull String repositoryId,
            @NotNull String workspaceId,
            @NotNull CommitAuthorConfig author
    ) {
        try (DataformClient client = DataformClient.create()) {
            String wsName = workspaceName(projectId, location, repositoryId, workspaceId);

            // Étape 1 : commit les changements non commités du workspace
            CommitWorkspaceChangesRequest commitRequest = CommitWorkspaceChangesRequest.newBuilder()
                    .setName(wsName)
                    .setAuthor(CommitAuthor.newBuilder()
                            .setName(author.name())
                            .setEmailAddress(author.emailAddress())
                            .build())
                    .setCommitMessage("Changes from IntelliJ Dataform Plugin")
                    .build();
            client.commitWorkspaceChanges(commitRequest);

            // Étape 2 : push vers le remote Git
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


    @Override
    public void pull(
            @NotNull String projectId,
            @NotNull String location,
            @NotNull String repositoryId,
            @NotNull String workspaceId,
            @NotNull CommitAuthorConfig author
    ) {
        try (DataformClient client = DataformClient.create()) {
            CommitAuthor commitAuthor = CommitAuthor.newBuilder()
                    .setName(author.name())
                    .setEmailAddress(author.emailAddress())
                    .build();
            PullGitCommitsRequest request = PullGitCommitsRequest.newBuilder()
                    .setName(workspaceName(projectId, location, repositoryId, workspaceId))
                    .setAuthor(commitAuthor)
                    .build();
            client.pullGitCommits(request);
        } catch (IOException e) {
            throw new GcpApiException(
                    "Failed to create DataformClient — check Application Default Credentials.", e);
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
        try (DataformClient client = DataformClient.create()) {
            String wsName = workspaceName(projectId, location, repositoryId, workspaceId);

            // Écrire / mettre à jour les fichiers locaux
            for (Map.Entry<String, String> entry : filesToWrite.entrySet()) {
                WriteFileRequest request = WriteFileRequest.newBuilder()
                        .setWorkspace(wsName)
                        .setPath(entry.getKey())
                        .setContents(ByteString.copyFromUtf8(entry.getValue()))
                        .build();
                client.writeFile(request);
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
        } catch (Exception e) {
            throw new GcpApiException("Error syncing files to GCP Dataform workspace.", e);
        }
    }

    @Override
    @NotNull
    public Set<String> listAllPaths(
            @NotNull String projectId,
            @NotNull String location,
            @NotNull String repositoryId,
            @NotNull String workspaceId
    ) {
        try (DataformClient client = DataformClient.create()) {
            return new HashSet<>(listAllWorkspacePaths(
                    client, projectId, location, repositoryId, workspaceId, ""));
        } catch (IOException e) {
            throw new GcpApiException(
                    "Failed to create DataformClient — check Application Default Credentials.", e);
        }
    }


    /**
     * {@inheritDoc}
     *
     * <p>Each path is fetched individually via {@code ReadRepositoryFileRequest}.
     * Paths not found on the remote are silently skipped.
     *
     * @throws GcpApiException if the client cannot be created
     */
    @Override
    @NotNull
    public Map<String, String> readFilesFromRepository(
            @NotNull String projectId,
            @NotNull String location,
            @NotNull String repositoryId,
            @NotNull List<String> paths
    ) {
        Map<String, String> result = new LinkedHashMap<>();
        try (DataformClient client = DataformClient.create()) {
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

    @Override
    @NotNull
    public Map<String, String> readAllFiles(
            @NotNull String projectId,
            @NotNull String location,
            @NotNull String repositoryId,
            @Nullable String workspaceId
    ) {
        try (DataformClient client = DataformClient.create()) {
            if (workspaceId != null) {
                return readAllWorkspaceFiles(client, projectId, location, repositoryId, workspaceId);
            } else {
                return readAllRepositoryFiles(client, projectId, location, repositoryId);
            }
        } catch (IOException e) {
            throw new GcpApiException(
                    "Failed to create DataformClient — check Application Default Credentials.", e);
        } catch (GcpApiException e) {
            throw e;
        } catch (Exception e) {
            throw new GcpApiException("Error reading files from GCP Dataform.", e);
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
                // Workspace API retourne le path complet → pas de préfixage
                paths.add(entry.getFile());
            } else if (entry.hasDirectory()) {
                // Workspace API retourne le path complet → récurser directement
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
}
