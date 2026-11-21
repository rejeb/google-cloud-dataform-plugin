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
import com.google.cloud.dataform.v1.QueryDirectoryContentsRequest;
import com.google.cloud.dataform.v1.ReadFileRequest;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * The files of a workspace. Its listings already give paths from the workspace root.
 */
record WorkspaceFileSource(@NotNull String workspaceName, @NotNull DataformClient client) implements FileSource {

    @Override
    public @NotNull List<Entry> list(@NotNull String directory) {
        QueryDirectoryContentsRequest request = QueryDirectoryContentsRequest.newBuilder()
                .setWorkspace(workspaceName)
                .setPath(directory)
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

    @Override
    public @NotNull String read(@NotNull String path) {
        ReadFileRequest request = ReadFileRequest.newBuilder()
                .setWorkspace(workspaceName)
                .setPath(path)
                .build();
        return client.readFile(request).getFileContents().toStringUtf8();
    }
}
