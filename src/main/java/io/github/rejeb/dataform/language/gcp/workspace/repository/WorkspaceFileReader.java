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
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

import static io.github.rejeb.dataform.language.gcp.workspace.repository.DataformResourceNames.isEmptyRepoException;
import static io.github.rejeb.dataform.language.gcp.workspace.repository.DataformResourceNames.repositoryName;
import static io.github.rejeb.dataform.language.gcp.workspace.repository.DataformResourceNames.workspaceName;

/**
 * Reads Dataform files, from a workspace when one is given and from the repository's default
 * branch otherwise. Directories are walked recursively because the API lists one level at a time.
 *
 * <p>Every request is a network round trip, so the subdirectories of a level are listed together
 * and the files are read together, on an executor of their own: the walk never waits inside it for
 * another of its tasks, and it never borrows the JVM's common pool, which other code shares.</p>
 */
final class WorkspaceFileReader {

    private static final Logger LOG = Logger.getInstance(WorkspaceFileReader.class);
    private static final int READ_PARALLELISM = 8;
    private static final String NODE_MODULES = "node_modules";
    private static final ExecutorService READ_EXECUTOR =
            AppExecutorUtil.createBoundedApplicationPoolExecutor("Dataform workspace read", READ_PARALLELISM);

    private record Entry(@NotNull String path, boolean directory) {
    }

    private WorkspaceFileReader() {
    }

    @NotNull
    static Map<String, String> readAllRepositoryFiles(
            @NotNull String projectId,
            @NotNull String location,
            @NotNull String repositoryId,
            @NotNull DataformClient client
    ) {
        List<String> paths = listAllRepositoryPaths(projectId, location, repositoryId, "", client);
        return readAll(paths, path -> readRepositoryFile(projectId, location, repositoryId, path, client));
    }

    @NotNull
    static Map<String, String> readAllWorkspaceFiles(
            @NotNull String projectId,
            @NotNull String location,
            @NotNull String repositoryId,
            @NotNull String workspaceId,
            @NotNull DataformClient client
    ) {
        List<String> paths = listAllWorkspacePaths(projectId, location, repositoryId, workspaceId, "", client);
        return readAll(paths, path -> readWorkspaceFile(projectId, location, repositoryId, workspaceId, path, client));
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
    static List<String> listAllRepositoryPaths(
            @NotNull String projectId,
            @NotNull String location,
            @NotNull String repositoryId,
            @NotNull String directoryPath,
            @NotNull DataformClient client
    ) {
        return awaited(walk(directoryPath,
                directory -> repositoryLevel(projectId, location, repositoryId, directory, client)));
    }

    @NotNull
    static List<String> listAllWorkspacePaths(
            @NotNull String projectId,
            @NotNull String location,
            @NotNull String repositoryId,
            @NotNull String workspaceId,
            @NotNull String directoryPath,
            @NotNull DataformClient client
    ) {
        return awaited(walk(directoryPath,
                directory -> workspaceLevel(projectId, location, repositoryId, workspaceId, directory, client)));
    }

    @NotNull
    private static List<Entry> repositoryLevel(
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
            List<Entry> entries = new ArrayList<>();
            for (DirectoryEntry entry : client.queryRepositoryDirectoryContents(request).iterateAll()) {
                if (entry.hasFile()) {
                    entries.add(new Entry(childPath(directoryPath, entry.getFile()), false));
                } else if (entry.hasDirectory() && !entry.getDirectory().equals(NODE_MODULES)) {
                    entries.add(new Entry(childPath(directoryPath, entry.getDirectory()), true));
                }
            }
            return entries;
        } catch (RuntimeException e) {
            if (isEmptyRepoException(e)) {
                LOG.info("Repository \"" + repositoryId + "\" is empty, skipping directory listing.");
                return List.of();
            }
            throw e;
        }
    }

    @NotNull
    private static List<Entry> workspaceLevel(
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
        List<Entry> entries = new ArrayList<>();
        for (DirectoryEntry entry : client.queryDirectoryContents(request).iterateAll()) {
            if (entry.hasFile()) {
                entries.add(new Entry(entry.getFile(), false));
            } else if (entry.hasDirectory() && !entry.getDirectory().equals(NODE_MODULES)) {
                entries.add(new Entry(entry.getDirectory(), true));
            }
        }
        return entries;
    }

    @NotNull
    private static String childPath(@NotNull String directoryPath, @NotNull String name) {
        return directoryPath.isEmpty() ? name : directoryPath + "/" + name;
    }

    /**
     * The files under a directory, in the order the listings give them. A level is listed, then its
     * subdirectories all at once, and a level waits for its subdirectories by composition rather
     * than by blocking a thread of the executor.
     */
    @NotNull
    private static CompletableFuture<List<String>> walk(@NotNull String directory,
                                                        @NotNull Function<String, List<Entry>> lister) {
        return CompletableFuture.supplyAsync(() -> lister.apply(directory), READ_EXECUTOR)
                .thenCompose(entries -> {
                    List<CompletableFuture<List<String>>> parts = new ArrayList<>(entries.size());
                    for (Entry entry : entries) {
                        parts.add(entry.directory()
                                ? walk(entry.path(), lister)
                                : CompletableFuture.completedFuture(List.of(entry.path())));
                    }
                    return CompletableFuture.allOf(parts.toArray(CompletableFuture[]::new))
                            .thenApply(ignored -> {
                                List<String> paths = new ArrayList<>();
                                for (CompletableFuture<List<String>> part : parts) {
                                    paths.addAll(part.join());
                                }
                                return paths;
                            });
                });
    }

    @NotNull
    private static Map<String, String> readAll(@NotNull List<String> paths,
                                               @NotNull Function<String, String> reader) {
        List<CompletableFuture<String>> contents = new ArrayList<>(paths.size());
        for (String path : paths) {
            contents.add(CompletableFuture.supplyAsync(() -> reader.apply(path), READ_EXECUTOR));
        }
        Map<String, String> files = new LinkedHashMap<>();
        try {
            for (int i = 0; i < paths.size(); i++) {
                files.put(paths.get(i), awaited(contents.get(i)));
            }
        } catch (RuntimeException e) {
            contents.forEach(content -> content.cancel(false));
            throw e;
        }
        return files;
    }

    /**
     * The result of a task, failing with the error the task raised rather than with the wrapper the
     * future puts around it, so callers recognise an empty repository or an API error as before.
     */
    private static <T> T awaited(@NotNull CompletableFuture<T> future) {
        try {
            return future.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException runtime) throw runtime;
            if (e.getCause() instanceof Error error) throw error;
            throw e;
        }
    }
}
