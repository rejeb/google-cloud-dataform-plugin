/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
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


import com.intellij.execution.services.ServiceEventListener;
import com.intellij.execution.services.SimpleServiceViewDescriptor;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DeleteProvider;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import io.github.rejeb.dataform.language.gcp.execution.bigquery.BigQueryJobResult;
import io.github.rejeb.dataform.language.gcp.execution.bigquery.QueryResultsRegistry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class DataformServiceViewDescriptor extends SimpleServiceViewDescriptor implements UiDataProvider {
    private final BigQueryJobResult result;
    private final Project project;

    public DataformServiceViewDescriptor(Project project, BigQueryJobResult result) {
        super(result.tableName(), AllIcons.Providers.BigQuery);
        this.result = result;
        this.project = project;
    }

    @Override
    public @Nullable JComponent getContentComponent() {
        return new QueryExecutionPanel(project, result);
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
                        "Remove result for \"" + result.tableName() + "\" ?",
                        "Delete",
                        "Delete",
                        "Cancel",
                        Messages.getWarningIcon()
                );
                if (choice != Messages.YES) return;
                result.pagedResult().dispose();
                QueryResultsRegistry.getInstance(project).remove(result.tableName());
                project.getMessageBus()
                        .syncPublisher(ServiceEventListener.TOPIC)
                        .handle(ServiceEventListener.ServiceEvent
                                .createResetEvent(DataformQueryContributor.class));
            }

            @Override
            public boolean canDeleteElement(@NotNull DataContext ctx) {
                return true;
            }
        });
    }
}
