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

import com.google.cloud.dataform.v1.DataformClient;
import com.google.cloud.dataform.v1.DirectoryEntry;
import com.google.cloud.dataform.v1.QueryRepositoryDirectoryContentsRequest;
import com.google.cloud.dataform.v1.ReadRepositoryFileRequest;
import com.intellij.openapi.diagnostic.Logger;
import io.github.rejeb.dataform.language.gcp.common.RepositoryCoordinates;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static io.github.rejeb.dataform.language.gcp.workspace.repository.DataformResourceNames.isEmptyRepoException;

/**
 * The files of the repository's main branch. Its listings give names, turned here into paths, and
 * a repository with no commit yet lists nothing.
 */
record BranchFileSource(@NotNull RepositoryCoordinates repository, @NotNull DataformClient client)
        implements FileSource {

    private static final Logger LOG = Logger.getInstance(BranchFileSource.class);

    @Override
    public @NotNull List<Entry> list(@NotNull String directory) {
        QueryRepositoryDirectoryContentsRequest request = QueryRepositoryDirectoryContentsRequest.newBuilder()
                .setName(repository.repositoryName())
                .setPath(directory)
                .build();
        try {
            List<Entry> entries = new ArrayList<>();
            for (DirectoryEntry entry : client.queryRepositoryDirectoryContents(request).iterateAll()) {
                if (entry.hasFile()) {
                    entries.add(new Entry(childPath(directory, entry.getFile()), false));
                } else if (entry.hasDirectory() && !entry.getDirectory().equals(NODE_MODULES)) {
                    entries.add(new Entry(childPath(directory, entry.getDirectory()), true));
                }
            }
            return entries;
        } catch (RuntimeException e) {
            if (isEmptyRepoException(e)) {
                LOG.info("Repository \"" + repository.repositoryId() + "\" is empty, skipping directory listing.");
                return List.of();
            }
            throw e;
        }
    }

    @Override
    public @NotNull String read(@NotNull String path) {
        ReadRepositoryFileRequest request = ReadRepositoryFileRequest.newBuilder()
                .setName(repository.repositoryName())
                .setPath(path)
                .build();
        return client.readRepositoryFile(request).getContents().toStringUtf8();
    }

    private static @NotNull String childPath(@NotNull String directory, @NotNull String name) {
        return directory.isEmpty() ? name : directory + "/" + name;
    }
}
