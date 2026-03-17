package io.github.rejeb.dataform.language.gcp.execution.grid;

import com.google.cloud.bigquery.Field;
import com.google.cloud.bigquery.StandardSQLTypeName;
import com.intellij.database.datagrid.GridColumn;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Types;
import java.util.Collections;
import java.util.Set;

public class BqGridColumn implements GridColumn {

    private final int    index;
    private final String name;
    private final int    sqlType;

    public BqGridColumn(int index, @NotNull Field field) {
        this.index   = index;
        this.name    = field.getName();
        this.sqlType = toSqlType(field.getType().getStandardType());
    }

    private static int toSqlType(StandardSQLTypeName bqType) {
        return switch (bqType) {
            case INT64              -> Types.BIGINT;
            case FLOAT64            -> Types.DOUBLE;
            case BOOL               -> Types.BOOLEAN;
            case DATE               -> Types.DATE;
            case DATETIME, TIMESTAMP-> Types.TIMESTAMP;
            case NUMERIC, BIGNUMERIC-> Types.NUMERIC;
            case BYTES              -> Types.BINARY;
            default                 -> Types.VARCHAR;   // STRING, JSON, GEOGRAPHY…
        };
    }

    @Override public int    getColumnNumber() { return index; }
    @Override public String getName()         { return name; }
    @Override public int    getType()         { return sqlType; }
    @Override public @Nullable String getTypeName() { return null; }
    @Override public @NotNull Set<Attribute> getAttributes() { return Collections.emptySet(); }
    // getValue() est fourni par défaut via GridColumn → row.getValue(getColumnNumber())
}

