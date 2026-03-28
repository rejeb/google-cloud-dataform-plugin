/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. ...
 */
package io.github.rejeb.dataform.language.projectWizard;

import com.intellij.facet.ui.ValidationResult;
import com.intellij.ide.util.projectWizard.AbstractNewProjectStep;
import com.intellij.ide.util.projectWizard.ProjectSettingsStepBase;
import com.intellij.platform.DirectoryProjectGenerator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import io.github.rejeb.dataform.language.DataformIcons;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;

public final class DataformDirectoryProjectGenerator
        implements DirectoryProjectGenerator<DataformProjectSettings> {

    @Override
    public @NotNull @Nls String getName() {
        return "Dataform";
    }

    @Override
    public @Nullable Icon getLogo() {
        return DataformIcons.FILE;
    }

    @Override
    public void generateProject(@NotNull Project project,
                                @NotNull VirtualFile baseDir,
                                @NotNull DataformProjectSettings settings,
                                @NotNull com.intellij.openapi.module.Module module) {
        try {
            DataformProjectStructureBuilder.createProjectStructure(project, baseDir, settings);
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate Dataform project structure", e);
        }
    }

    @Override
    public @NotNull ValidationResult validate(@NotNull String baseDirPath) {
        return ValidationResult.OK;
    }
}