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
import com.intellij.ide.DeleteProvider;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.UiDataProvider;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.pom.Navigatable;
import com.intellij.ui.SimpleTextAttributes;
import io.github.rejeb.dataform.language.gcp.execution.bigquery.QueryResultsRegistry;
import io.github.rejeb.dataform.language.gcp.execution.bigquery.QueryRun;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * The Services node of a query run: its status, its table and the time it started.
 */
public class DataformServiceViewDescriptor implements ServiceViewDescriptor, UiDataProvider {

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

    private final QueryRun run;
    private final Project project;

    public DataformServiceViewDescriptor(@NotNull Project project, @NotNull QueryRun run) {
        this.run = run;
        this.project = project;
    }

    @Override
    public @NotNull ItemPresentation getPresentation() {
        PresentationData presentation = new PresentationData();
        presentation.setIcon(QueryRunIcons.of(run.state()));
        presentation.addText(run.shortName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
        presentation.addText("  " + CLOCK.withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(run.createdAt())),
                SimpleTextAttributes.GRAYED_ATTRIBUTES);
        presentation.setTooltip(run.tableName());
        return presentation;
    }

    @Override
    public @NotNull String getId() {
        return run.tableName();
    }

    @Override
    public @Nullable JComponent getContentComponent() {
        return QueryResultPanels.getInstance(project).panelFor(run);
    }

    @Override
    public @NotNull ActionGroup getToolbarActions() {
        return new DefaultActionGroup(new RemoveFinishedQueriesAction());
    }

    @Override
    public @Nullable Navigatable getNavigatable() {
        return run.source() != null ? new OpenFileDescriptor(project, run.source()) : null;
    }

    @Override
    public void uiDataSnapshot(@NotNull DataSink sink) {
        sink.set(PlatformDataKeys.DELETE_ELEMENT_PROVIDER, new DeleteProvider() {

            @Override
            public @NotNull ActionUpdateThread getActionUpdateThread() {
                return ActionUpdateThread.EDT;
            }

            @Override
            public void deleteElement(@NotNull DataContext ctx) {
                int choice = Messages.showYesNoDialog(
                        project,
                        "Remove result for \"" + run.tableName() + "\" ?",
                        "Delete",
                        "Delete",
                        "Cancel",
                        Messages.getWarningIcon()
                );
                if (choice != Messages.YES) return;
                QueryResultsRegistry.getInstance(project).remove(run.tableName());
                QueryResultsView.refresh(project);
            }

            @Override
            public boolean canDeleteElement(@NotNull DataContext ctx) {
                return true;
            }
        });
    }
}
