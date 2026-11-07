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
import com.intellij.ui.treeStructure.Tree;
import io.github.rejeb.dataform.language.DataformIcons;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.InvocationActionResult;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.InvocationActionState;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.WorkflowInvocationState;

import javax.swing.tree.DefaultMutableTreeNode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class WorkflowInvocationTreeCellRendererTest extends BasePlatformTestCase {

    private static final Instant START = Instant.parse("2026-11-01T10:00:00Z");

    public void testAnActionShowsItsNameThenItsSchemaGrayed() {
        InvocationActionResult action = new InvocationActionResult("p.ds.users", InvocationActionState.FAILED,
                "Not found", START, START.plusSeconds(41), null, null, null, null, null);

        WorkflowInvocationTreeCellRenderer renderer = render(action);

        assertSame(DataformIcons.STATUS_FAILED, renderer.getIcon());
        assertEquals(List.of("users", "ds"), fragments(renderer, null));
        assertEquals(List.of("ds"), fragments(renderer, SimpleTextAttributes.GRAYED_ATTRIBUTES));
        assertEquals("41 s", renderer.rightText());
        assertEquals("Not found", renderer.getToolTipText());
    }

    public void testAWaitingActionShowsNoDuration() {
        InvocationActionResult action = new InvocationActionResult("p.ds.users", InvocationActionState.PENDING,
                null, null, null, null, null, null, null, null);

        WorkflowInvocationTreeCellRenderer renderer = render(action);

        assertSame(DataformIcons.STATUS_PENDING, renderer.getIcon());
        assertNull(renderer.rightText());
        assertEquals("p.ds.users", renderer.getToolTipText());
    }

    public void testARunningActionCountsItsTimeSoFar() {
        InvocationActionResult action = new InvocationActionResult("p.ds.users", InvocationActionState.RUNNING,
                null, Instant.now().minusSeconds(3), null, null, null, null, null, null);

        WorkflowInvocationTreeCellRenderer renderer = render(action);

        assertSame(DataformIcons.SPINNER, renderer.getIcon());
        assertNotNull(renderer.rightText());
    }

    public void testTheRootShowsTheRunConfigurationInBoldAndTheElapsedTime() {
        WorkflowInvocationTreeModel.InvocationRootNode root = new WorkflowInvocationTreeModel.InvocationRootNode(
                "Full workflow", WorkflowInvocationState.SUCCEEDED, Duration.ofMillis(22_500));

        WorkflowInvocationTreeCellRenderer renderer = render(root);

        assertSame(DataformIcons.STATUS_SUCCEEDED, renderer.getIcon());
        assertEquals(List.of("Full workflow"), fragments(renderer, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES));
        assertEquals("22.5 s", renderer.rightText());
    }

    public void testSkippedAndCancelledActionsUseThePrototypeIcons() {
        assertSame(DataformIcons.STATUS_SKIPPED, ActionStateIcons.of(InvocationActionState.SKIPPED));
        assertSame(DataformIcons.STATUS_SKIPPED, ActionStateIcons.of(InvocationActionState.DISABLED));
        assertSame(DataformIcons.STATUS_CANCELLED, ActionStateIcons.of(InvocationActionState.CANCELLED));
        assertSame(DataformIcons.STATUS_SUCCEEDED, ActionStateIcons.of(InvocationActionState.SUCCEEDED));
        assertSame(DataformIcons.SPINNER, ActionStateIcons.of(WorkflowInvocationState.RUNNING));
    }

    private static WorkflowInvocationTreeCellRenderer render(Object value) {
        WorkflowInvocationTreeCellRenderer renderer = new WorkflowInvocationTreeCellRenderer();
        Tree tree = new Tree();
        renderer.getTreeCellRendererComponent(tree, new DefaultMutableTreeNode(value), false, false, true, 0, false);
        return renderer;
    }

    private static List<String> fragments(SimpleColoredComponent component, SimpleTextAttributes only) {
        List<String> texts = new ArrayList<>();
        SimpleColoredComponent.ColoredIterator it = component.iterator();
        while (it.hasNext()) {
            String text = it.next().strip();
            if (text.isEmpty()) continue;
            if (only == null || only.equals(it.getTextAttributes())) texts.add(text);
        }
        return texts;
    }
}
