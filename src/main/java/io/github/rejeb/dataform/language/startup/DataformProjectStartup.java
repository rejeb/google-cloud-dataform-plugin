package io.github.rejeb.dataform.language.startup;

import com.intellij.facet.FacetManager;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.startup.ProjectActivity;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import io.github.rejeb.dataform.projectWizard.DataformFacet;
import io.github.rejeb.dataform.projectWizard.DataformFacetType;
import io.github.rejeb.dataform.projectWizard.DataformModuleType;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;

public class DataformProjectStartup implements ProjectActivity {

    private static final Logger LOG = Logger.getInstance(DataformProjectStartup.class);

    @Nullable
    @Override
    public Object execute(@NotNull Project project,
                          @NotNull Continuation<? super Unit> continuation) {

        VirtualFile baseDir = ProjectUtil.guessProjectDir(project);
        if (baseDir == null) return null;

        boolean isDataformProject =
                baseDir.findChild("dataform.json") != null ||
                        baseDir.findChild("workflow_settings.yaml") != null;

        if (!isDataformProject) return null;

        // Step 1: Recreate module with correct type if needed
        recreateModuleWithCorrectType(project);

        // Step 2: Add facet ONLY on modules with correct type
        WriteAction.runAndWait(() -> {
            for (Module module : ModuleManager.getInstance(project).getModules()) {

                // ✅ Guard: only add facet to DATAFORM_MODULE type modules
                if (!DataformModuleType.ID.equals(module.getModuleTypeName())) {
                    continue;
                }

                FacetManager facetManager = FacetManager.getInstance(module);
                if (facetManager.getFacetByType(DataformFacetType.ID) == null) {
                    ModifiableFacetModel model = facetManager.createModifiableModel();
                    DataformFacet facet = facetManager.createFacet(
                            DataformFacetType.INSTANCE, "Dataform", null);
                    model.addFacet(facet);
                    model.commit();
                }
            }
        });

        return null;
    }


    private void recreateModuleWithCorrectType(@NotNull Project project) {
        ModuleManager moduleManager = ModuleManager.getInstance(project);

        for (Module module : moduleManager.getModules()) {
            if (DataformModuleType.ID.equals(module.getModuleTypeName())) {
                continue;
            }

            VirtualFile[] contentRoots = ModuleRootManager.getInstance(module)
                    .getContentRoots();
            String imlPath = module.getModuleFilePath();

            WriteAction.runAndWait(() -> {
                ModifiableModuleModel moduleModel = moduleManager.getModifiableModel();
                moduleModel.disposeModule(module);

                VirtualFile staleIml = LocalFileSystem.getInstance()
                        .findFileByPath(imlPath);
                if (staleIml != null) {
                    try {
                        staleIml.delete(this);
                    } catch (IOException e) {
                        LOG.warn("Could not delete stale .iml: " + imlPath, e);
                    }
                }

                Module newModule = moduleModel.newModule(
                        Path.of(imlPath),
                        DataformModuleType.ID
                );
                moduleModel.commit();

                ModifiableRootModel rootModel = ModuleRootManager
                        .getInstance(newModule)
                        .getModifiableModel();
                for (VirtualFile contentRoot : contentRoots) {
                    rootModel.addContentEntry(contentRoot);
                }
                rootModel.commit();

                LOG.info("Recreated module as DATAFORM_MODULE: " + imlPath);
            });
        }
    }
}
