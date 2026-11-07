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

import java.util.List;

/**
 * Snapshot of a workflow invocation at a given polling tick.
 */
public record WorkflowInvocationProgress(
        @NotNull String invocationName,
        @NotNull WorkflowInvocationState state,
        @NotNull List<InvocationActionResult> actions,
        @Nullable InvocationSummary summary,
        @Nullable String errorMessage
) {
    public WorkflowInvocationProgress(
            @NotNull String invocationName,
            @NotNull WorkflowInvocationState state,
            @NotNull List<InvocationActionResult> actions,
            @Nullable InvocationSummary summary) {
        this(invocationName, state, actions, summary, null);
    }

    public boolean isTerminal() {
        return state != WorkflowInvocationState.RUNNING;
    }

    /**
     * Whether an action of this snapshot has yet to reach its final state.
     */
    public boolean hasUnendedActions() {
        return actions.stream().anyMatch(WorkflowInvocationProgress::isUnended);
    }

    /**
     * This snapshot, with the run still running.
     */
    @NotNull
    public WorkflowInvocationProgress asStillRunning() {
        return new WorkflowInvocationProgress(invocationName, WorkflowInvocationState.RUNNING, actions, summary,
                errorMessage);
    }

    /**
     * This snapshot, with the actions that have yet to reach their final state marked unknown.
     */
    @NotNull
    public WorkflowInvocationProgress withUnendedActionsUnknown() {
        List<InvocationActionResult> ended = actions.stream()
                .map(action -> isUnended(action) ? unknown(action) : action)
                .toList();
        return new WorkflowInvocationProgress(invocationName, state, ended, summary, errorMessage);
    }

    private static boolean isUnended(@NotNull InvocationActionResult action) {
        return action.state() == InvocationActionState.PENDING || action.state() == InvocationActionState.RUNNING;
    }

    @NotNull
    private static InvocationActionResult unknown(@NotNull InvocationActionResult action) {
        return new InvocationActionResult(action.target(), InvocationActionState.UNKNOWN, action.failureReason(),
                action.startTime(), action.endTime(), action.jobId(), action.jobProject(), action.jobLocation(),
                action.jobDataset(), action.sqlScript());
    }
}
