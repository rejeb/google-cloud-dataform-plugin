package io.github.rejeb.dataform.language.schema.sql.model;

import com.intellij.database.model.DasColumn;
import com.intellij.database.model.DasObject;
import com.intellij.database.model.DasTable;
import com.intellij.database.model.ObjectKind;
import com.intellij.util.containers.JBIterable;
import io.github.rejeb.dataform.language.schema.sql.ColumnInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

// DataformDasTable.java
public class DataformDasTable implements DasTable {
    private final DataformDasSchema myParent;
    private final String myName;
    private final List<ColumnInfo> myColumns;
    public DataformDasTable(DataformDasSchema myParent,
                            String table, List<ColumnInfo> myColumns) {
        this.myColumns = myColumns;
        this.myParent = myParent;
        this.myName = table;
    }

    @Override
    public @NotNull String getName() {
        return myName;
    }

    @Override
    public @Nullable DasObject getDasParent() {
        return myParent;
    }

    @Override
    public @NotNull ObjectKind getKind() {
        return ObjectKind.TABLE;
    }

    @Override
    public boolean isQuoted() {
        return false;
    }

    @Override
    public boolean isSystem() {
        return false;
    }

    @Override
    public boolean isTemporary() {
        return false;
    }

    @Override
    public @NotNull Set<DasColumn.Attribute> getColumnAttrs(@Nullable DasColumn columnInfo) {
        return Set.of();
    }

    @Override
    public @NotNull JBIterable<? extends DasObject> getDasChildren(@Nullable ObjectKind kind) {
        if (kind == ObjectKind.COLUMN) {
            return JBIterable.from(myColumns)
                    .map(col -> new DataformDasColumn(null, col));
        }
        return JBIterable.empty();
    }
}
