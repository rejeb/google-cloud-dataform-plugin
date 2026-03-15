package io.github.rejeb.dataform.language.gcp.toolwindow.action;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import io.github.rejeb.dataform.language.gcp.toolwindow.DataformGcpPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public class RefreshAction extends AnAction {

    private final Supplier<@Nullable String> workspaceIdSupplier;
    private final DataformGcpPanel.PanelCallback callback;

    public RefreshAction(
            @NotNull Supplier<@Nullable String> workspaceIdSupplier,
            @NotNull DataformGcpPanel.PanelCallback callback
    ) {
        super(() -> "Refresh Workspaces",
                AllIcons.Actions.Refresh);
        this.workspaceIdSupplier = workspaceIdSupplier;
        this.callback = callback;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        callback.onRefreshWorkspaces();
        callback.onFetch(workspaceIdSupplier.get());
    }
}