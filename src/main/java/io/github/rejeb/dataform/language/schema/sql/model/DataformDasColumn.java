package io.github.rejeb.dataform.language.schema.sql.model;

import com.intellij.database.model.DasColumn;
import com.intellij.database.model.DasObject;
import com.intellij.database.model.DasTable;
import com.intellij.database.model.ObjectKind;
import com.intellij.database.model.properties.PropertyConverter;
import com.intellij.database.types.DasType;
import io.github.rejeb.dataform.language.schema.sql.ColumnInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// DataformDasColumn.java
public class DataformDasColumn implements DasColumn {
    private final DataformDasTable myParent;
    private final ColumnInfo myInfo;

    public DataformDasColumn(DataformDasTable parent, ColumnInfo info) {
        this.myParent = parent;
        this.myInfo = info;
    }

    @Override
    public @NotNull String getName() {
        return myInfo.name();
    }

    @Override
    public @Nullable DasObject getDasParent() {
        return myParent;
    }

    @Override
    public @NotNull ObjectKind getKind() {
        return ObjectKind.COLUMN;
    }

    @Override
    public boolean isQuoted() {
        return false;
    }

    @Override
    public boolean isNotNull() {
        return "REQUIRED".equals(myInfo.mode());
    }

    @Override
    public @Nullable String getDefault() {
        return myInfo.name();
    }

    @Override
    public @NotNull DasType getDasType() {
        return PropertyConverter.importDasType(myInfo.type());
    }

    @Override
    public short getPosition() {
        return 0;
    }

    @Override
    public @Nullable DasTable getTable() {
        return null;
    }
}

