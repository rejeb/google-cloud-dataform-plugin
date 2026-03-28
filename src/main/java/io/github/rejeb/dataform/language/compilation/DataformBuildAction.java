package io.github.rejeb.dataform.language.compilation;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsActions;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class DataformBuildAction extends AnAction {
    private final DataformBuildContext context;
    private final Project project;

    public DataformBuildAction(@NotNull DataformBuildContext context,
                               @NotNull Project project,
                               @NotNull @NlsActions.ActionText String text,
                               @NotNull @NlsActions.ActionDescription String description,
                               @NotNull Icon icon) {
        super(text, description, icon);
        this.context = context;
        this.project = project;
    }

    public DataformBuildAction(@NotNull Project project,
                               @NotNull @NlsActions.ActionText String text,
                               @NotNull @NlsActions.ActionDescription String description,
                               @NotNull Icon icon) {
        super(text, description, icon);
        this.context = new DataformBuildContext(project);
        context.finished(true, "Build finished");
        this.project = project;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        ApplicationManager.getApplication().executeOnPooledThread(() ->
                DataformBuildManager.build(project)
        );
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabled(context.result.isDone());
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
