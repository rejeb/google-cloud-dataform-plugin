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
package io.github.rejeb.dataform.language.gcp.execution.workflow.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;

/**
 * Static metadata about a workflow invocation, populated on first progress fetch.
 */
public record InvocationSummary(
        @NotNull String invocationName,
        @NotNull String compilationResultName,
        @NotNull String sourceType,
        @Nullable String sourceWorkspaceName,
        @Nullable String contents,
        @NotNull Instant startTime,
        @Nullable Instant endTime
) {
    /**
     * Returns the short ID extracted from the compilation result resource name
     * (last path segment after the final '/').
     */
    @NotNull
    public String compilationResultId() {
        int idx = compilationResultName.lastIndexOf('/');
        return idx >= 0 ? compilationResultName.substring(idx + 1) : compilationResultName;
    }

    public static final String CONSOLE_URL = "https://console.cloud.google.com/";

    /**
     * Returns the GCP Console URL for this workflow invocation.
     */
    @NotNull
    public String gcpConsoleUrl() {
        String url = dataformConsoleUrl(invocationName, "workflows");
        return url != null ? url : CONSOLE_URL;
    }

    /**
     * Returns the GCP Console URL for the source workspace, or null if not a workspace source.
     */
    @Nullable
    public String workspaceConsoleUrl() {
        return sourceWorkspaceName == null ? null : dataformConsoleUrl(sourceWorkspaceName, "workspaces");
    }

    @Nullable
    private static String dataformConsoleUrl(@NotNull String resourceName, @NotNull String page) {
        String[] parts = resourceName.split("/");
        if (parts.length < 8) return null;
        return CONSOLE_URL + "bigquery/dataform/locations/" + parts[3] + "/repositories/" + parts[5]
                + "/" + page + "/" + parts[7] + "?project=" + parts[1];
    }
}
