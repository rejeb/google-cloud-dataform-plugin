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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Reads the repositories the Dataform API returns.
 */
final class DataformRepositoryMapper {

    private DataformRepositoryMapper() {
    }

    static @NotNull List<DataformRepository> toDataformRepositories(@NotNull Iterable<Repository> repositories) {
        List<DataformRepository> result = new ArrayList<>();
        for (Repository repository : repositories) {
            result.add(toDataformRepository(repository));
        }
        result.sort(Comparator.comparing(DataformRepository::repositoryId, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    static @NotNull DataformRepository toDataformRepository(@NotNull Repository repository) {
        String name = repository.getName();
        String id = name.substring(name.lastIndexOf('/') + 1);
        Repository.GitRemoteSettings git = repository.getGitRemoteSettings();
        String url = blankToNull(git.getUrl());
        return new DataformRepository(id, url, url == null ? null : blankToNull(git.getDefaultBranch()),
                repository.getServiceAccount());
    }

    private static @Nullable String blankToNull(@NotNull String text) {
        return text.isBlank() ? null : text;
    }
}
