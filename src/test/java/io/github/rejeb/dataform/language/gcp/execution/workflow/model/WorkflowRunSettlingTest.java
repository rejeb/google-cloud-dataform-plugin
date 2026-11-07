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

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class WorkflowRunSettlingTest {

    @Test
    public void aRunningSnapshotIsShownAsItIs() {
        WorkflowInvocationProgress running = progress(WorkflowInvocationState.RUNNING, InvocationActionState.RUNNING);

        assertSame(running, new WorkflowRunSettling(3).settle(running));
    }

    @Test
    public void aRunWhoseActionsEndedIsOver() {
        WorkflowInvocationProgress failed = progress(WorkflowInvocationState.FAILED,
                InvocationActionState.FAILED, InvocationActionState.SKIPPED);

        assertSame(failed, new WorkflowRunSettling(3).settle(failed));
    }

    @Test
    public void aRunEndingBeforeItsActionsIsShownRunningUntilTheyEnd() {
        WorkflowRunSettling settling = new WorkflowRunSettling(3);

        WorkflowInvocationProgress shown = settling.settle(progress(WorkflowInvocationState.FAILED,
                InvocationActionState.RUNNING, InvocationActionState.PENDING));
        assertFalse(shown.isTerminal());

        WorkflowInvocationProgress settled = progress(WorkflowInvocationState.FAILED,
                InvocationActionState.FAILED, InvocationActionState.SKIPPED);
        assertSame(settled, settling.settle(settled));
    }

    @Test
    public void actionsThatNeverEndStopBeingAwaited() {
        WorkflowRunSettling settling = new WorkflowRunSettling(2);
        WorkflowInvocationProgress stuck = progress(WorkflowInvocationState.CANCELLED,
                InvocationActionState.RUNNING, InvocationActionState.SUCCEEDED);

        assertFalse(settling.settle(stuck).isTerminal());
        assertFalse(settling.settle(stuck).isTerminal());
        WorkflowInvocationProgress last = settling.settle(stuck);

        assertTrue(last.isTerminal());
        assertEquals(WorkflowInvocationState.CANCELLED, last.state());
        assertEquals(List.of(InvocationActionState.UNKNOWN, InvocationActionState.SUCCEEDED),
                last.actions().stream().map(InvocationActionResult::state).toList());
    }

    @Test
    public void anUnknownActionIsNotAwaited() {
        WorkflowInvocationProgress failed = progress(WorkflowInvocationState.FAILED, InvocationActionState.UNKNOWN);

        assertSame(failed, new WorkflowRunSettling(3).settle(failed));
    }

    private static WorkflowInvocationProgress progress(WorkflowInvocationState state,
                                                       InvocationActionState... actions) {
        List<InvocationActionResult> results = java.util.Arrays.stream(actions)
                .map(action -> new InvocationActionResult("p.d." + action, action, null, null, null,
                        null, null, null, null, null))
                .toList();
        return new WorkflowInvocationProgress("invocation", state, results, null);
    }
}
