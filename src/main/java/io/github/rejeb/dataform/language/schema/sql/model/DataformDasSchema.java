package io.github.rejeb.dataform.language.schema.sql.model;

import com.intellij.database.model.DasNamespace;
import com.intellij.database.model.DasObject;
import com.intellij.database.model.ObjectKind;
import com.intellij.util.containers.JBIterable;
import io.github.rejeb.dataform.language.schema.sql.ColumnInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// DataformDasSchema.java
public class DataformDasSchema implements DasNamespace {
    private final DataformDasCatalog myParent;
    private final String myDataset;
    private final List<DataformDasTable> myTables;

    public DataformDasSchema(DataformDasCatalog parent, String dataset,
                             List<Map.Entry<String, List<ColumnInfo>>> tables) {
        this.myParent = parent;
        this.myDataset = dataset;
        this.myTables = new ArrayList<>();
        for (Map.Entry<String, List<ColumnInfo>> e : tables) {
            String[] parts = e.getKey().split("\\.", 3);
            myTables.add(new DataformDasTable(this, parts[2], e.getValue()));
        }
    }

    @Override
    public @NotNull String getName() {
        return myDataset;
    }

    @Override
    public @Nullable DasObject getDasParent() {
        return myParent;
    }

    @Override
    public @NotNull ObjectKind getKind() {
        return ObjectKind.SCHEMA;
    }

    @Override
    public boolean isQuoted() {
        return false;
    }

    @Override
    public @NotNull JBIterable<? extends DasObject> getDasChildren(@Nullable ObjectKind kind) {
        if (kind == ObjectKind.TABLE) {
            return JBIterable.from(myTables);
        }
        return JBIterable.empty();
    }
}

