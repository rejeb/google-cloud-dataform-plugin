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
package io.github.rejeb.dataform.language.gcp.execution.bigquery.serviceview;

import com.intellij.execution.services.ServiceViewDescriptor;
import com.intellij.execution.services.SimpleServiceViewDescriptor;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBPanelWithEmptyText;
import io.github.rejeb.dataform.language.DataformIcons;
import io.github.rejeb.dataform.language.gcp.execution.bigquery.QueryResultsRegistry;
import io.github.rejeb.dataform.language.ui.EmptyStates;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;

/**
 * The Dataform Queries node of the Services view, the parent of every query run of a project.
 *
 * @param project the project whose query runs the node lists
 */
public record QueryGroup(@NotNull Project project) {

    static final String NAME = "Dataform Queries";

    /**
     * The node: the bold name, the grayed number of runs, and the action removing the finished ones.
     */
    public @NotNull ServiceViewDescriptor getViewDescriptor() {
        return new SimpleServiceViewDescriptor(NAME, DataformIcons.QUERIES) {

            @Override
            public @NotNull ItemPresentation getPresentation() {
                PresentationData presentation = new PresentationData();
                presentation.setIcon(DataformIcons.QUERIES);
                presentation.addText(NAME, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
                presentation.addText("  " + QueryResultsRegistry.getInstance(project).getAll().size(),
                        SimpleTextAttributes.GRAYED_ATTRIBUTES);
                return presentation;
            }

            @Override
            public @NotNull ActionGroup getToolbarActions() {
                return new DefaultActionGroup(new RemoveFinishedQueriesAction());
            }

            @Override
            public @NotNull JComponent getContentComponent() {
                JBPanelWithEmptyText panel = new JBPanelWithEmptyText();
                EmptyStates.show(panel.getEmptyText(), "Select a query");
                return panel;
            }
        };
    }
}
