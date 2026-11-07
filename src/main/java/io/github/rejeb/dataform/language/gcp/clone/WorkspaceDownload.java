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
package io.github.rejeb.dataform.language.gcp.clone;

import com.intellij.openapi.progress.ProgressIndicator;
import io.github.rejeb.dataform.language.gcp.workspace.repository.WorkspaceRepository;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Copies the files of a workspace into the directory of a new project and opens it. A repository
 * without a Git remote has nothing to clone, and the result is not under version control.
 */
final class WorkspaceDownload {

    private final WorkspaceRepository repository;
    private final PendingRepositoryLinks links;
    private final Consumer<Path> projectOpener;

    WorkspaceDownload(@NotNull WorkspaceRepository repository,
                      @NotNull PendingRepositoryLinks links,
                      @NotNull Consumer<Path> projectOpener) {
        this.repository = repository;
        this.links = links;
        this.projectOpener = projectOpener;
    }

    void run(@NotNull RepositoryCheckoutRequest request, @NotNull ProgressIndicator indicator) throws IOException {
        indicator.setIndeterminate(true);
        indicator.checkCanceled();
        Map<String, String> files = repository.readAllFiles(request.projectId(), request.location(),
                request.repository().repositoryId(), request.workspaceId());
        indicator.checkCanceled();
        write(request.directory(), files);
        links.register(request.directory(), request.link());
        projectOpener.accept(request.directory());
    }

    private static void write(@NotNull Path directory, @NotNull Map<String, String> files) throws IOException {
        Map<Path, String> targets = targets(directory, files);
        Files.createDirectories(directory);
        for (Map.Entry<Path, String> target : targets.entrySet()) {
            Files.createDirectories(target.getKey().getParent());
            Files.writeString(target.getKey(), target.getValue(), StandardCharsets.UTF_8);
        }
    }

    private static @NotNull Map<Path, String> targets(@NotNull Path directory, @NotNull Map<String, String> files)
            throws IOException {
        Path root = directory.toAbsolutePath().normalize();
        Map<Path, String> targets = new LinkedHashMap<>();
        for (Map.Entry<String, String> file : files.entrySet()) {
            targets.put(target(root, file.getKey()), file.getValue());
        }
        return targets;
    }

    private static @NotNull Path target(@NotNull Path root, @NotNull String relativePath) throws IOException {
        try {
            Path target = root.resolve(relativePath).normalize();
            if (target.startsWith(root) && !target.equals(root)) {
                return target;
            }
        } catch (InvalidPathException e) {
            throw new IOException("The workspace file \"" + relativePath + "\" has an invalid path.", e);
        }
        throw new IOException("The workspace file \"" + relativePath + "\" is outside the project directory.");
    }
}
