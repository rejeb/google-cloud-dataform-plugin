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

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.DataformIcons;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.InvocationActionResult;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.InvocationActionState;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.InvocationSummary;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.WorkflowInvocationProgress;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.WorkflowInvocationState;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

public class WorkflowExecutionPanelTest extends BasePlatformTestCase {

    private static final String INVOCATION = "projects/p/locations/l/repositories/r/workflowInvocations/i";
    private static final Instant START = Instant.parse("2026-11-02T20:52:49Z");

    public void testTheRunToolbarOffersFailedOnlyAndTheConsoleLikeThePrototype() {
        WorkflowExecutionPanel panel = new WorkflowExecutionPanel("Full workflow", getProject());
        try {
            List<AnAction> actions = Arrays.asList(panel.toolbarActions());
            List<String> texts = actions.stream().map(action -> action.getTemplatePresentation().getText()).toList();

            assertEquals(List.of("Show Failed Only", "Open in Google Cloud Console"), texts);
            assertTrue(actions.get(0) instanceof ToggleAction);
            assertSame(DataformIcons.FAILED_ONLY, actions.get(0).getTemplatePresentation().getIcon());
            assertSame(DataformIcons.EXTERNAL_LINK, actions.get(1).getTemplatePresentation().getIcon());
        } finally {
            panel.release();
        }
    }

    public void testShowFailedOnlyFiltersTheModel() {
        WorkflowExecutionPanel panel = new WorkflowExecutionPanel("Full workflow", getProject());
        try {
            panel.showFailedOnly(true);

            assertTrue(panel.treeModel().isFailedOnly());
        } finally {
            panel.release();
        }
    }

    public void testTheRootIsTitledByTheRunConfiguration() {
        WorkflowExecutionPanel panel = new WorkflowExecutionPanel("Full workflow", getProject());
        try {
            panel.updateProgress(progress(WorkflowInvocationState.RUNNING, action("a", InvocationActionState.RUNNING)));

            assertEquals("Full workflow", panel.treeModel().rootNode().title());
            assertNull(panel.selectedTarget());
            assertTrue(panel.isRootSelected());
        } finally {
            panel.release();
        }
    }

    public void testAFailedRunSelectsItsFirstFailedAction() {
        WorkflowExecutionPanel panel = new WorkflowExecutionPanel("Full workflow", getProject());
        try {
            panel.updateProgress(progress(WorkflowInvocationState.RUNNING, action("a", InvocationActionState.RUNNING)));
            panel.updateProgress(progress(WorkflowInvocationState.FAILED, action("a", InvocationActionState.SUCCEEDED),
                    action("b", InvocationActionState.FAILED), action("c", InvocationActionState.FAILED)));

            assertEquals("p.ds.b", panel.selectedTarget());
        } finally {
            panel.release();
        }
    }

    public void testASucceededRunKeepsTheRootSelected() {
        WorkflowExecutionPanel panel = new WorkflowExecutionPanel("Full workflow", getProject());
        try {
            panel.updateProgress(progress(WorkflowInvocationState.SUCCEEDED, action("a", InvocationActionState.SUCCEEDED)));

            assertTrue(panel.isRootSelected());
        } finally {
            panel.release();
        }
    }

    public void testSelectingATargetShowsItsAction() {
        WorkflowExecutionPanel panel = new WorkflowExecutionPanel("Full workflow", getProject());
        try {
            panel.updateProgress(progress(WorkflowInvocationState.SUCCEEDED, action("a", InvocationActionState.SUCCEEDED),
                    action("b", InvocationActionState.SUCCEEDED)));

            panel.select("p.ds.b");

            assertEquals("p.ds.b", panel.selectedTarget());
        } finally {
            panel.release();
        }
    }

    private static WorkflowInvocationProgress progress(WorkflowInvocationState state, InvocationActionResult... actions) {
        InvocationSummary summary = new InvocationSummary(INVOCATION, "c", "WORKSPACE", null, null, START,
                state == WorkflowInvocationState.RUNNING ? null : START.plusSeconds(20));
        return new WorkflowInvocationProgress(INVOCATION, state, List.of(actions), summary);
    }

    private static InvocationActionResult action(String name, InvocationActionState state) {
        return new InvocationActionResult("p.ds." + name, state, state == InvocationActionState.FAILED ? "boom" : null,
                START, state == InvocationActionState.RUNNING ? null : START.plusSeconds(4), null, null, null, null, null);
    }
}
