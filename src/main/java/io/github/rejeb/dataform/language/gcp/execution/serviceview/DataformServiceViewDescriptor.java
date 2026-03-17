package io.github.rejeb.dataform.language.gcp.execution.serviceview;

import com.intellij.execution.services.ServiceEventListener;
import com.intellij.execution.services.SimpleServiceViewDescriptor;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DeleteProvider;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import io.github.rejeb.dataform.language.gcp.bigquery.BigQueryJobResult;
import io.github.rejeb.dataform.language.gcp.execution.QueryResultsRegistry;
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
