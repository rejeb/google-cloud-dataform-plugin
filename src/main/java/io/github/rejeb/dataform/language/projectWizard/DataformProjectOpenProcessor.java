package io.github.rejeb.dataform.language.projectWizard;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.PlatformProjectOpenProcessor;
import com.intellij.projectImport.ProjectOpenProcessor;
import io.github.rejeb.dataform.language.DataformIcons;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import javax.swing.*;

public final class DataformProjectOpenProcessor extends ProjectOpenProcessor {
    private final PlatformProjectOpenProcessor delegate = new PlatformProjectOpenProcessor();

    @Override
    public @NotNull String getName() {
        return "Dataform";
    }

    @Override
    public Icon getIcon() {
        return DataformIcons.FILE;
    }

    @Override
    public boolean canOpenProject(@NotNull VirtualFile file) {
        if (file.isDirectory()) {
            return file.findChild("workflow_settings.yaml") != null
                    || file.findChild("dataform.json") != null;
        }
        return false;
    }

    @Override
    public @Nullable Project doOpenProject(@NonNull VirtualFile virtualFile, @Nullable Project project, boolean b) {
        return delegate.doOpenProject(virtualFile, project, b);
    }
}