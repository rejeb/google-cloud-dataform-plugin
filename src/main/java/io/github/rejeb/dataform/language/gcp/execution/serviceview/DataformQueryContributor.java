package io.github.rejeb.dataform.language.gcp.execution.serviceview;

import com.intellij.execution.services.ServiceViewContributor;
import com.intellij.execution.services.ServiceViewDescriptor;
import com.intellij.execution.services.SimpleServiceViewDescriptor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import io.github.rejeb.dataform.language.gcp.execution.QueryResultsRegistry;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Root contributor registered in plugin.xml.
 * Visible in the Services tool window only when results exist.
 */
public final class DataformQueryContributor
        implements ServiceViewContributor<QueryResultNode> {

    @Override
    public @NotNull ServiceViewDescriptor getViewDescriptor(@NotNull Project project) {
        return new SimpleServiceViewDescriptor("Dataform Queries", AllIcons.Nodes.DataTables);
    }

    @Override
    public @NotNull List<QueryResultNode> getServices(@NotNull Project project) {
        return QueryResultsRegistry.getInstance(project)
                .getAll()
                .stream()
                .map(QueryResultNode::new)
                .toList();
    }

    @Override
    public @NotNull ServiceViewDescriptor getServiceDescriptor(
            @NotNull Project project,
            @NotNull QueryResultNode node
    ) {
        return node.getViewDescriptor(project);
    }
}
