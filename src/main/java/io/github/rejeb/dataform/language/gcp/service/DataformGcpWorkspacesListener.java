package io.github.rejeb.dataform.language.gcp.service;

import com.intellij.util.messages.Topic;
import io.github.rejeb.dataform.language.gcp.workspace.Workspace;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface DataformGcpWorkspacesListener {

    Topic<DataformGcpWorkspacesListener> TOPIC = Topic.create(
            "DataformGcpWorkspaces",
            DataformGcpWorkspacesListener.class
    );

    void onWorkspacesLoaded(@NotNull List<Workspace> workspaces);
}
