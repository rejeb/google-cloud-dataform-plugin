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

import io.github.rejeb.dataform.language.gcp.common.CommitAuthorConfig;
import io.github.rejeb.dataform.language.gcp.common.GcpConfigProvider;
import io.github.rejeb.dataform.language.gcp.common.GcpIdentityResolver;
import io.github.rejeb.dataform.language.service.WorkflowSettingsProperty;
import io.github.rejeb.dataform.language.service.WorkflowSettingsService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class WorkflowSettingsGcpConfigProvider implements GcpConfigProvider {

    private final GcpRepositorySettings gcpRepositorySettings;

    public WorkflowSettingsGcpConfigProvider(
            @NotNull GcpRepositorySettings gcpRepositorySettings
    ) {
        this.gcpRepositorySettings = gcpRepositorySettings;
    }

    @Override
    public @Nullable String getProjectId() {
        return gcpRepositorySettings.getProjectId();
    }

    @Override
    public @Nullable String getLocation() {
        return gcpRepositorySettings.getLocation();
    }

    @Override
    public @Nullable String getRepositoryId() {
        return gcpRepositorySettings.getRepositoryId();
    }


    /**
     * Reads author name and email from the IntelliJ Git plugin user config.
     * Falls back to system username if Git plugin is unavailable.
     */
    @Override
    @NotNull
    public CommitAuthorConfig getCommitAuthor() {

        return GcpIdentityResolver.resolve();
    }
}
