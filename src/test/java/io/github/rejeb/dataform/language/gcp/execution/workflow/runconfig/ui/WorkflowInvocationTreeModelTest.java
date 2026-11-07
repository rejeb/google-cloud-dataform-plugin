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

import io.github.rejeb.dataform.language.gcp.execution.workflow.model.InvocationActionResult;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.InvocationActionState;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.WorkflowInvocationProgress;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.WorkflowInvocationState;
import org.junit.jupiter.api.Test;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowInvocationTreeModelTest {

    @Test
    void failedOnlyKeepsTheFailedActions() {
        WorkflowInvocationTreeModel model = new WorkflowInvocationTreeModel("inv");
        model.update(progress(action("a", InvocationActionState.SUCCEEDED), action("b", InvocationActionState.FAILED),
                action("c", InvocationActionState.SKIPPED)));

        model.setFailedOnly(true);

        assertTrue(model.isFailedOnly());
        assertEquals(List.of("b"), targets(model));
        model.setFailedOnly(false);
        assertEquals(List.of("a", "b", "c"), targets(model));
    }

    @Test
    void anActionThatFailsLaterAppearsWhileFailedOnly() {
        WorkflowInvocationTreeModel model = new WorkflowInvocationTreeModel("inv");
        model.setFailedOnly(true);

        model.update(progress(action("a", InvocationActionState.RUNNING)));
        assertEquals(List.of(), targets(model));
        model.update(progress(action("a", InvocationActionState.FAILED)));

        assertEquals(List.of("a"), targets(model));
    }

    @Test
    void failedOnlyIsOffByDefault() {
        assertFalse(new WorkflowInvocationTreeModel("inv").isFailedOnly());
    }

    private static List<String> targets(WorkflowInvocationTreeModel model) {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) model.getRoot();
        List<String> targets = new ArrayList<>();
        for (int i = 0; i < root.getChildCount(); i++) {
            targets.add(((InvocationActionResult) ((DefaultMutableTreeNode) root.getChildAt(i)).getUserObject()).target());
        }
        return targets;
    }

    private static WorkflowInvocationProgress progress(InvocationActionResult... actions) {
        return new WorkflowInvocationProgress("inv", WorkflowInvocationState.RUNNING, List.of(actions), null);
    }

    private static InvocationActionResult action(String target, InvocationActionState state) {
        return new InvocationActionResult(target, state, null, null, null, null, null, null, null, null);
    }
}
