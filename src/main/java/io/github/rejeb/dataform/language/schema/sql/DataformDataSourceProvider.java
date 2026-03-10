package io.github.rejeb.dataform.language.schema.sql;

import com.intellij.database.model.DasModel;
import com.intellij.database.model.RawDataSource;
import com.intellij.database.psi.DataSourceManager;
import com.intellij.database.psi.DbDataSource;
import com.intellij.database.psi.DbDataSourceImpl;
import com.intellij.database.psi.DbPsiFacade;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.sql.psi.impl.DataSourceProvider;
import io.github.rejeb.dataform.language.schema.sql.model.DataformDasModel;
import io.github.rejeb.dataform.language.service.DataformCompilationService;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class DataformDataSourceProvider implements DataSourceProvider {
    private final List<DbDataSource> dbDataSources = new ArrayList<>();

    @Override
    public synchronized @NotNull List<DbDataSource> getDataSources(@NotNull PsiFile file) {
        Project project = file.getProject();
        PsiFile topLevel = InjectedLanguageManager.getInstance(project)
                .getTopLevelFile(file);
        if (topLevel == null || !topLevel.getName().endsWith(".sqlx")) {
            return Collections.emptyList();
        }
        List<DbDataSource> dataSources = DbPsiFacade.getInstance(project).getDataSources();
        DataformTableSchemaService dtss =
                project.getService(DataformTableSchemaService.class);
        DataformCompilationService svc =
                project.getService(DataformCompilationService.class);
        String projectId = svc.getCompiledGraph().getProjectConfig().getDefaultDatabase();
        if (projectId == null || projectId.isBlank()) return List.of();
        DataSourceManager<RawDataSource> manager = DataformDatasourceManager.getInstance(project);
        DbDataSource bqDs = findBigQueryDS(dataSources, projectId).orElse(null);
        if (dbDataSources.isEmpty()) {

            Map<String, List<ColumnInfo>> cache = dtss.getAllSchemas();
            if (cache.isEmpty()) return Collections.emptyList();

            DasModel model = new DataformDasModel(cache);

            RawDataSource ds = new DataformDbDataSource(model, bqDs);
            manager.addDataSource(ds);
            DbDataSource dataSource = new DbDataSourceImpl(project, ds, manager);
            dataSource.setName(projectId);
            dbDataSources.add(dataSource);
        }
        return dbDataSources;
    }

    private Optional<DbDataSource> findBigQueryDS(@NotNull List<DbDataSource> datasources,
                                                  @NotNull String projectId) {

        return datasources.stream().filter(ds -> matchesBigQueryProject(ds, projectId)).findFirst();
    }

    private static boolean matchesBigQueryProject(@NotNull DbDataSource ds,
                                                  @NotNull String projectId) {
        try {

            String url = ds.getConnectionConfig().getUrl();
            if (url != null && url.toLowerCase().contains("bigquery")
                    && url.contains(projectId)) return true;
        } catch (Exception ignored) {
        }
        return ds.getName().contains(projectId);
    }

}
