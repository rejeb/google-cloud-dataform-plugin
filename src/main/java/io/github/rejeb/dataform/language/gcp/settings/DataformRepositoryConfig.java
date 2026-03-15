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
package io.github.rejeb.dataform.language.gcp.settings;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Immutable snapshot of the Dataform GCP repository configuration.
 *
 * @param projectId    GCP project ID hosting the Dataform repository
 * @param repositoryId Dataform repository name
 * @param location     GCP location (e.g. "europe-west1")
 */
public record DataformRepositoryConfig(
        @NotNull String projectId,
        @NotNull String repositoryId,
        @NotNull String location
) {
    /** @return {@code true} if all fields are non-blank */
    public boolean isComplete() {
        return !projectId.isBlank() && !repositoryId.isBlank() && !location.isBlank();
    }

    /** @return {@code null} if any field is blank, otherwise {@code this} */
    @Nullable
    public DataformRepositoryConfig orNull() {
        return isComplete() ? this : null;
    }
}
