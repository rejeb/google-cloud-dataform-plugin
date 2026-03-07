package io.github.rejeb.dataform.projectWizard;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.ProjectImportProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DataformProjectImportProvider extends ProjectImportProvider {

    public DataformProjectImportProvider() {
        super(new DataformProjectImportBuilder());
    }

    @Override
    public boolean canImport(@NotNull VirtualFile fileOrDirectory, @Nullable Project project) {
        // Activer uniquement pour les dossiers Dataform
        if (!fileOrDirectory.isDirectory()) return false;
        return fileOrDirectory.findChild("dataform.json") != null ||
                fileOrDirectory.findChild("workflow_settings.yaml") != null;
    }
}

