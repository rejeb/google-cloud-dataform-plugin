package io.github.rejeb.dataform.language.schema.sql;

import com.intellij.database.connectivity.dataSource.BasicDataSourceManager;
import com.intellij.database.dialects.DatabaseDialectEx;
import com.intellij.database.model.DasDataSource;
import com.intellij.database.model.DasModel;
import com.intellij.database.model.RawDataSource;
import com.intellij.database.psi.DataSourceManager;
import com.intellij.database.psi.DbDataSource;
import com.intellij.database.psi.DbPsiFacade;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.sql.dialects.bigquery.BigQueryDialect;
import com.intellij.util.Consumer;
import io.github.rejeb.dataform.language.schema.sql.model.DataformDasModel;
import io.github.rejeb.dataform.language.service.DataformCompilationService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import javax.swing.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class DataformDatasourceManager extends BasicDataSourceManager<RawDataSource> {

    protected DataformDatasourceManager(@NotNull Project project) {
        super(project, List.of());
    }

    public static DataSourceManager<RawDataSource> getInstance(Project project) {
        return EP_NAME.findExtensionOrFail(DataformDatasourceManager.class, project);
    }

    @Override
    public @Nullable DatabaseDialectEx getDatabaseDialect(@NonNull RawDataSource rawDataSource) {
        return new com.intellij.database.dialects.bigquery.BigQueryDialect();
    }

    @Override
    public @Nullable Language getQueryLanguage(@NonNull RawDataSource rawDataSource) {
        return BigQueryDialect.INSTANCE;
    }

    @Override
    public @Nullable Language getPushedQueryLanguage(@NonNull RawDataSource rawDataSource) {
        return null;
    }


    @Override
    public boolean containsDataSource(@NonNull RawDataSource element) {
        return super.containsDataSource(element);
    }

    @Override
    public void addDataSource(@NonNull RawDataSource element) {
        super.attachDataSource(element);
    }

    @Override
    public void removeDataSource(@NonNull RawDataSource element) {
        super.detachDataSource(element);
    }

    @Override
    public boolean isLoading(@NonNull RawDataSource element) {
        return false;
    }


    @Override
    public @Nullable AnAction getCreateDataSourceAction(@NotNull Consumer<? super RawDataSource> consumer) {
        return null;
    }

    @Override
    public @NonNull RawDataSource createEmpty() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public @NonNull RawDataSource copyDataSource(@NotNull String newName, @NonNull RawDataSource copyFrom) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void renameDataSource(@NonNull RawDataSource element, @NotNull String name) {

    }

    @Override
    public void setGroupName(@NonNull RawDataSource dataSource, @Nullable String groupName) {

    }

    @Override
    public boolean isMyDataSource(@NotNull Class<? extends DasDataSource> clazz) {
        return false;
    }

    @Override
    public @NotNull Configurable createDataSourceEditor(@NonNull RawDataSource rawDataSource) {
        return new Configurable() {
            @Override
            public @NlsContexts.ConfigurableName String getDisplayName() {
                return "";
            }

            @Override
            public @Nullable JComponent createComponent() {
                return null;
            }

            @Override
            public boolean isModified() {
                return false;
            }

            @Override
            public void apply() throws ConfigurationException {

            }
        };
    }

}
