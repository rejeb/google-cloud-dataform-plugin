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

import com.google.cloud.dataform.v1.*;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static io.github.rejeb.dataform.language.gcp.workspace.repository.DataformResourceNames.isEmptyRepoException;
import static io.github.rejeb.dataform.language.gcp.workspace.repository.DataformResourceNames.repositoryName;
import static io.github.rejeb.dataform.language.gcp.workspace.repository.DataformResourceNames.workspaceName;

/**
 * Reads Dataform files, from a workspace when one is given and from the repository's default
 * branch otherwise. Directories are walked recursively because the API lists one level at a time.
 */
final class WorkspaceFileReader {

    private static final Logger LOG = Logger.getInstance(WorkspaceFileReader.class);

    private WorkspaceFileReader() {
    }

    @NotNull
    static Map<String, String> readAllRepositoryFiles(
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
    static Map<String, String> readAllWorkspaceFiles(
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
    static String readRepositoryFile(
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
    static String readWorkspaceFile(
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
    static Stream<String> listAllRepositoryPaths(
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
    static Stream<String> resolveRepositoryEntry(
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
    static Stream<String> listAllWorkspacePaths(
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
    static Stream<String> resolveWorkspaceEntry(
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
}
