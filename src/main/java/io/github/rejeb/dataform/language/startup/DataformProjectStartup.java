package io.github.rejeb.dataform.language.startup;

import com.intellij.facet.FacetManager;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.startup.ProjectActivity;
import com.intellij.openapi.vfs.VirtualFile;
import io.github.rejeb.dataform.language.schema.dts.DataformDtsGenerator;
import io.github.rejeb.dataform.projectWizard.DataformFacet;
import io.github.rejeb.dataform.projectWizard.DataformFacetType;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

        WriteAction.runAndWait(() -> {
            project.getService(DataformDtsGenerator.class).generateDts();
            ModuleManager moduleManager = ModuleManager.getInstance(project);

            Module[] modules = moduleManager.getModules();

            for (Module module : modules) {
                FacetManager facetManager = FacetManager.getInstance(module);

                if (facetManager.getFacetByType(DataformFacetType.ID) == null) {
                    ModifiableFacetModel model = facetManager.createModifiableModel();
                    DataformFacet facet = facetManager.createFacet(
                            DataformFacetType.INSTANCE, "Dataform", null);
                    model.addFacet(facet);
                    model.commit();
                    LOG.info("Dataform facet added to module: " + module.getName());
                }
            }
        });

        return null;
    }
}
