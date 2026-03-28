package io.github.rejeb.dataform.language.compilation;

import com.intellij.task.ProjectTask;
import org.jetbrains.annotations.NotNull;

/** Marker task pour que DataformBuildTaskRunner puisse filtrer via canRun(). */
public final class DataformBuildableTask implements ProjectTask {
    @Override
    public @NotNull String getPresentableName() {
        return "Dataform Compile";
    }
}