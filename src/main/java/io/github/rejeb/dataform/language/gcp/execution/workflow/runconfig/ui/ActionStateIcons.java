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
package io.github.rejeb.dataform.language.gcp.execution.workflow.runconfig.ui;

import io.github.rejeb.dataform.language.DataformIcons;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.InvocationActionState;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.WorkflowInvocationState;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;

/**
 * The status icons of a workflow run, shared by its tree, its status line and its detail headers so
 * they always agree; they are the prototype's.
 */
final class ActionStateIcons {

    private ActionStateIcons() {
    }

    @NotNull
    static Icon of(@NotNull WorkflowInvocationState state) {
        return switch (state) {
            case RUNNING -> DataformIcons.SPINNER;
            case SUCCEEDED -> DataformIcons.STATUS_SUCCEEDED;
            case FAILED -> DataformIcons.STATUS_FAILED;
            case CANCELLED -> DataformIcons.STATUS_CANCELLED;
            case UNKNOWN -> DataformIcons.STATUS_PENDING;
        };
    }

    @NotNull
    static Icon of(@NotNull InvocationActionState state) {
        return switch (state) {
            case RUNNING -> DataformIcons.SPINNER;
            case SUCCEEDED -> DataformIcons.STATUS_SUCCEEDED;
            case FAILED -> DataformIcons.STATUS_FAILED;
            case SKIPPED, DISABLED -> DataformIcons.STATUS_SKIPPED;
            case CANCELLED -> DataformIcons.STATUS_CANCELLED;
            case PENDING, UNKNOWN -> DataformIcons.STATUS_PENDING;
        };
    }

    /**
     * The icon of a BigQuery job status: {@code DONE}, {@code FAILED}, {@code RUNNING}, anything else
     * waiting.
     */
    @NotNull
    static Icon ofJob(@NotNull String status) {
        return switch (status) {
            case "DONE" -> DataformIcons.STATUS_SUCCEEDED;
            case "FAILED" -> DataformIcons.STATUS_FAILED;
            case "RUNNING" -> DataformIcons.SPINNER;
            default -> DataformIcons.STATUS_PENDING;
        };
    }
}
