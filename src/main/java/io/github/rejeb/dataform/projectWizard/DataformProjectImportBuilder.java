package io.github.rejeb.dataform.projectWizard;

import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.ModifiableArtifactModel;
import com.intellij.projectImport.ProjectImportBuilder;
import io.github.rejeb.dataform.language.DataformIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public class DataformProjectImportBuilder extends ProjectImportBuilder<VirtualFile> {

    @Override
    public @NotNull String getName() {
        return "Dataform";
    }

    @Override
    public Icon getIcon() {
        return DataformIcons.FILE;
    }

    @Override
    public List<VirtualFile> getList() { return List.of(); }

    @Override
    public boolean isMarked(VirtualFile element) { return false; }

    @Override
    public void setList(List<VirtualFile> list) {}

    @Override
    public void setOpenProjectSettingsAfter(boolean on) {}

    @Override
    public @Nullable List<Module> commit(Project project, ModifiableModuleModel model,
                                         ModulesProvider modulesProvider,
                                         ModifiableArtifactModel artifactModel) {
        Module module = model.newModule(
                project.getBasePath() + "/" + project.getName() + ".iml",
                DataformModuleType.ID
        );
        ModifiableRootModel rootModel = ModuleRootManager.getInstance(module).getModifiableModel();
        VirtualFile baseDir = ProjectUtil.guessProjectDir(project);
        if (baseDir != null) {
            rootModel.addContentEntry(baseDir);
        }
        rootModel.commit();
        return List.of(module);
    }
}
