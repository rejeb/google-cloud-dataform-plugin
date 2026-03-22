package io.github.rejeb.dataform.language.fileEditor.action;

import com.intellij.build.BuildContentManager;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class RunBuildAction extends AnAction {
    private final Project project;

    public RunBuildAction(@NotNull Project project) {
        super("Run Build", "Run the Dataform workflow for this table", AllIcons.Toolwindows.ToolWindowBuild);
        this.project = project;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        CompilerManager.getInstance(project).rebuild((aborted, errors, warnings, compileContext) -> {
            if (!aborted && errors > 0) {
                SwingUtilities.invokeLater(() ->
                        BuildContentManager.getInstance(project).getOrCreateToolWindow().show(null)
                );
            }
        });
    }


    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
    }

}
