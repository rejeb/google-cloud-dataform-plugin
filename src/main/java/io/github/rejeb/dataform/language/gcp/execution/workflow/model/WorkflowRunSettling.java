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

/**
 * Decides when a polled workflow run is over. The Dataform API can report the end of an invocation up
 * to a second before the end of its actions, so a run ending while an action has not is shown as still
 * running for a few more polls. Past them, it is shown as it ended, with those actions marked unknown.
 * Holds the polls of one run; they must not overlap.
 */
public final class WorkflowRunSettling {

    private final int maxPollsAwaited;
    private int pollsAwaited;

    /**
     * @param maxPollsAwaited how many snapshots may be held back waiting for the actions to end
     */
    public WorkflowRunSettling(int maxPollsAwaited) {
        this.maxPollsAwaited = maxPollsAwaited;
    }

    /**
     * The snapshot to show for the one just polled.
     */
    @NotNull
    public WorkflowInvocationProgress settle(@NotNull WorkflowInvocationProgress polled) {
        if (!polled.isTerminal() || !polled.hasUnendedActions()) {
            return polled;
        }
        if (pollsAwaited++ < maxPollsAwaited) {
            return polled.asStillRunning();
        }
        return polled.withUnendedActionsUnknown();
    }
}
