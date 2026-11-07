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

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import io.github.rejeb.dataform.language.DataformIcons;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.InvocationActionResult;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.InvocationActionState;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.InvocationSummary;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.WorkflowInvocationProgress;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.WorkflowInvocationState;

import java.time.Instant;
import java.util.List;

public class RunStatusHeaderTest extends BasePlatformTestCase {

    private static final Instant START = Instant.parse("2026-11-02T20:52:49Z");

    public void testAFinishedRunSaysHowEachActionEnded() {
        RunStatusHeader header = new RunStatusHeader();

        header.update(progress(WorkflowInvocationState.FAILED, START.plusMillis(22_500),
                action(InvocationActionState.SUCCEEDED), action(InvocationActionState.SUCCEEDED),
                action(InvocationActionState.FAILED), action(InvocationActionState.SKIPPED)));

        assertSame(DataformIcons.STATUS_FAILED, header.line().getIcon());
        assertEquals("Failed 2 succeeded · 1 failed · 1 skipped · 22.5 s", text(header.line()));
        assertEquals(List.of("Failed"), fragments(header.line(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES));
        assertFalse(header.progressBar().isVisible());
    }

    public void testTheFailedCountIsRed() {
        RunStatusHeader header = new RunStatusHeader();

        header.update(progress(WorkflowInvocationState.FAILED, START.plusSeconds(3), action(InvocationActionState.FAILED)));

        assertEquals(List.of("1 failed"), fragments(header.line(), RunStatusHeader.FAILED_COUNT));
    }

    public void testARunningRunCountsTheFinishedActionsAndShowsABar() {
        RunStatusHeader header = new RunStatusHeader();

        header.update(progress(WorkflowInvocationState.RUNNING, null,
                action(InvocationActionState.SUCCEEDED), action(InvocationActionState.RUNNING),
                action(InvocationActionState.PENDING)));

        assertTrue(text(header.line()).startsWith("Running 1 of 3"));
        assertNull(header.line().getIcon());
        assertTrue(header.progressBar().isVisible());
        assertTrue(header.progressBar().isIndeterminate());
    }

    public void testBeforeTheInvocationExistsItShowsTheStep() {
        RunStatusHeader header = new RunStatusHeader();

        header.update(new WorkflowInvocationProgress("Creating temporary workspace", WorkflowInvocationState.RUNNING,
                List.of(), null));

        assertEquals("Creating temporary workspace…", text(header.line()));
        assertTrue(header.progressBar().isVisible());
    }

    static String text(SimpleColoredComponent component) {
        StringBuilder text = new StringBuilder();
        SimpleColoredComponent.ColoredIterator it = component.iterator();
        while (it.hasNext()) {
            text.append(it.next());
        }
        return text.toString().replaceAll("\\s+", " ").strip();
    }

    static List<String> fragments(SimpleColoredComponent component, SimpleTextAttributes only) {
        List<String> texts = new java.util.ArrayList<>();
        SimpleColoredComponent.ColoredIterator it = component.iterator();
        while (it.hasNext()) {
            String text = it.next().replace("·", "").strip();
            if (!text.isEmpty() && only.equals(it.getTextAttributes())) texts.add(text);
        }
        return texts;
    }

    private static WorkflowInvocationProgress progress(WorkflowInvocationState state, Instant end,
                                                       InvocationActionResult... actions) {
        InvocationSummary summary = new InvocationSummary("projects/p/locations/l/repositories/r/workflowInvocations/i",
                "c", "WORKSPACE", null, null, START, end);
        return new WorkflowInvocationProgress("projects/p/locations/l/repositories/r/workflowInvocations/i", state,
                List.of(actions), summary);
    }

    private static int counter;

    private static InvocationActionResult action(InvocationActionState state) {
        return new InvocationActionResult("p.ds.a" + (counter++), state, null, null, null, null, null, null, null, null);
    }
}
