package io.github.rejeb.dataform.language.gcp.execution.workflow.runconfig;

import com.intellij.execution.*;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import io.github.rejeb.dataform.language.compilation.DataformCompilationService;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.Mode;
import io.github.rejeb.dataform.language.gcp.settings.GcpRepositorySettings;
import org.jetbrains.annotations.NotNull;

public class RunSqlxHelper {

    public static void launchFromTags(@NotNull Project project,
                                      @NotNull VirtualFile file,
                                      boolean deps,
                                      boolean dependants,
                                      boolean fullRefresh) {
        RunManager runManager = RunManager.getInstance(project);
        DataformWorkflowConfigurationType type = ConfigurationTypeUtil.findConfigurationType(
                DataformWorkflowConfigurationType.class);
        DataformWorkflowConfigurationFactory factory =
                (DataformWorkflowConfigurationFactory) type.getConfigurationFactories()[0];
        CompiledGraph graphs = DataformCompilationService.getInstance(project).getCompiledGraph();
        RunnerAndConfigurationSettings settings =
                runManager.createConfiguration(file.getNameWithoutExtension(), factory);
        DataformWorkflowRunConfiguration config =
                (DataformWorkflowRunConfiguration) settings.getConfiguration();
        GcpRepositorySettings gcpRepositorySettings = GcpRepositorySettings.getInstance(project);
        config.setIncludedTags(
                graphs.getTags(file.getCanonicalPath())
        );
        config.setWorkspaceId(gcpRepositorySettings.getSelectedWorkspaceId());
        config.setSelectedMode(Mode.TAGS);
        config.setTransitiveDependenciesIncluded(deps);
        config.setTransitiveDependentsIncluded(dependants);
        config.setFullyRefreshIncrementalTables(fullRefresh);

        runManager.addConfiguration(settings);
        runManager.setSelectedConfiguration(settings);
        settings.setTemporary(true);
        settings.setEditBeforeRun(false);
        settings.setActivateToolWindowBeforeRun(true);
        Executor executorById = ExecutorRegistry.getInstance()
                .getExecutorById(DefaultRunExecutor.EXECUTOR_ID);
        ProgramRunnerUtil.executeConfiguration(settings,
                executorById != null ? executorById : new DefaultRunExecutor());
    }
}