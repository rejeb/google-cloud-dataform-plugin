package io.github.rejeb.dataform.language.gcp.execution.serviceview;

import com.intellij.execution.services.ServiceEventListener;
import com.intellij.execution.services.ServiceViewDescriptor;
import com.intellij.execution.services.SimpleServiceViewDescriptor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import io.github.rejeb.dataform.language.gcp.bigquery.BigQueryJobResult;
import io.github.rejeb.dataform.language.gcp.execution.QueryResultsRegistry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public final class QueryResultNode {

    private final BigQueryJobResult result;

    public QueryResultNode(@NotNull BigQueryJobResult result) {
        this.result = result;
    }

    public @NotNull BigQueryJobResult getResult() {
        return result;
    }

    public @NotNull ServiceViewDescriptor getViewDescriptor(@NotNull Project project) {
        return new DataformServiceViewDescriptor(project, result);
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof QueryResultNode other)) return false;
        return result.tableName().equals(other.result.tableName());
    }

    @Override
    public int hashCode() {
        return result.tableName().hashCode();
    }
}
