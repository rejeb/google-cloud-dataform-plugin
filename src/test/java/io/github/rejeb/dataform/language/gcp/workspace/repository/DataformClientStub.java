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
import com.google.cloud.dataform.v1.QueryRepositoryDirectoryContentsRequest;
import io.github.rejeb.dataform.language.gcp.common.RepositoryCoordinates;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class DataformClientStub {

    static final RepositoryCoordinates COORDINATES = new RepositoryCoordinates("p", "eu", "r");
    static final String REPOSITORY = "projects/p/locations/eu/repositories/r";
    static final String WORKSPACE = REPOSITORY + "/workspaces/w";

    private DataformClientStub() {
    }

    static void repositoryDirectory(DataformClient client, String path, DirectoryEntry... entries) {
        DataformClient.QueryRepositoryDirectoryContentsPagedResponse response =
                mock(DataformClient.QueryRepositoryDirectoryContentsPagedResponse.class);
        when(response.iterateAll()).thenReturn(List.of(entries));
        when(client.queryRepositoryDirectoryContents(QueryRepositoryDirectoryContentsRequest.newBuilder()
                .setName(REPOSITORY).setPath(path).build()))
                .thenReturn(response);
    }

    static void workspaceDirectory(DataformClient client, String path, DirectoryEntry... entries) {
        DataformClient.QueryDirectoryContentsPagedResponse response =
                mock(DataformClient.QueryDirectoryContentsPagedResponse.class);
        when(response.iterateAll()).thenReturn(List.of(entries));
        when(client.queryDirectoryContents(QueryDirectoryContentsRequest.newBuilder()
                .setWorkspace(WORKSPACE).setPath(path).build()))
                .thenReturn(response);
    }

    static DirectoryEntry file(String path) {
        return DirectoryEntry.newBuilder().setFile(path).build();
    }

    static DirectoryEntry directory(String path) {
        return DirectoryEntry.newBuilder().setDirectory(path).build();
    }
}
