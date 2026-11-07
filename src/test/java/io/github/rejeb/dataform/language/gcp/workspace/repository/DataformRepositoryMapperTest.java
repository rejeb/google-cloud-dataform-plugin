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

import com.google.cloud.dataform.v1.Repository;
import io.github.rejeb.dataform.language.gcp.workspace.DataformRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DataformRepositoryMapperTest {

    private static Repository.Builder named(String id) {
        return Repository.newBuilder().setName("projects/acme/locations/europe-west1/repositories/" + id);
    }

    @Test
    void aRepositoryLinkedToGitKeepsItsRemoteDefaultBranchAndServiceAccount() {
        Repository repository = named("analytics-core")
                .setServiceAccount("dataform@acme.iam.gserviceaccount.com")
                .setGitRemoteSettings(Repository.GitRemoteSettings.newBuilder()
                        .setUrl("https://github.com/acme/analytics-core.git")
                        .setDefaultBranch("develop"))
                .build();

        assertEquals(new DataformRepository("analytics-core", "https://github.com/acme/analytics-core.git",
                        "develop", "dataform@acme.iam.gserviceaccount.com"),
                DataformRepositoryMapper.toDataformRepositories(List.of(repository)).getFirst());
    }

    @Test
    void aRepositoryWithoutGitSettingsHasNoRemote() {
        assertEquals(new DataformRepository("finance-marts", null, null, ""),
                DataformRepositoryMapper.toDataformRepositories(List.of(named("finance-marts").build())).getFirst());
    }

    @Test
    void aBlankRemoteUrlCountsAsNoRemote() {
        Repository repository = named("sandbox")
                .setGitRemoteSettings(Repository.GitRemoteSettings.newBuilder().setUrl(" "))
                .build();

        assertEquals(new DataformRepository("sandbox", null, null, ""),
                DataformRepositoryMapper.toDataformRepositories(List.of(repository)).getFirst());
    }

    @Test
    void repositoriesAreSortedByIdIgnoringCase() {
        List<DataformRepository> repositories = DataformRepositoryMapper.toDataformRepositories(List.of(
                named("sandbox").build(), named("Analytics").build(), named("finance").build()));

        assertEquals(List.of("Analytics", "finance", "sandbox"),
                repositories.stream().map(DataformRepository::repositoryId).toList());
    }

    @Test
    void readsOneRepositoryWithItsGitRemote() {
        Repository repository = named("sales").setGitRemoteSettings(Repository.GitRemoteSettings.newBuilder()
                .setUrl("https://example.com/sales.git").setDefaultBranch("develop")).build();

        assertEquals(new DataformRepository("sales", "https://example.com/sales.git", "develop", ""),
                DataformRepositoryMapper.toDataformRepository(repository));
    }
}
