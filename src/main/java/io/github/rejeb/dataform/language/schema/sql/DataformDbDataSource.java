package io.github.rejeb.dataform.language.schema.sql;

import com.intellij.database.Dbms;
import com.intellij.database.dataSource.AbstractDataSource;
import com.intellij.database.model.DasModel;
import com.intellij.database.model.NameVersion;
import com.intellij.database.model.RawConnectionConfig;
import com.intellij.database.psi.DbDataSource;
import com.intellij.database.util.Version;
import io.github.rejeb.dataform.language.DataformIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class DataformDbDataSource extends AbstractDataSource {
    private final DasModel dasModel;
    private final DbDataSource parent;

    public DataformDbDataSource(DasModel dasModel, DbDataSource parent) {
        this.dasModel = dasModel;

        this.parent = parent;
    }

    @Override
    public @NotNull NameVersion getDatabaseVersion() {
        return parent != null ? parent.getDatabaseVersion() : NameVersion.UNKNOWN;
    }

    @Override
    public @Nullable RawConnectionConfig getConnectionConfig() {
        return parent != null ? parent.getConnectionConfig() : null;
    }

    @Override
    public @NotNull DasModel getModel() {
        return this.dasModel;
    }

    @Override
    public @NotNull Dbms getDbms() {
        return parent != null ? parent.getDbms() : Dbms.BIGQUERY;
    }

    @Override
    public @Nullable Version getVersion() {
        return parent != null ? parent.getVersion() : null;
    }

    @Override
    public Icon getIcon(int i) {
        return DataformIcons.FILE;
    }
}
