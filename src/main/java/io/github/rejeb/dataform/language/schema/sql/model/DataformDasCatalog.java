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

// DataformDasCatalog.java
public class DataformDasCatalog implements DasNamespace {
    private final String myProject;
    private final List<DataformDasSchema> mySchemas;

    public DataformDasCatalog(String project,
                              Map<String, List<Map.Entry<String, List<ColumnInfo>>>> datasets) {
        this.myProject = project;
        this.mySchemas = new ArrayList<>();
        for (Map.Entry<String, List<Map.Entry<String, List<ColumnInfo>>>> e : datasets.entrySet()) {
            mySchemas.add(new DataformDasSchema(this, e.getKey(), e.getValue()));
        }
    }

    @Override
    public @NotNull String getName() {
        return myProject;
    }

    @Override
    public @NotNull ObjectKind getKind() {
        return ObjectKind.DATABASE;
    }

    @Override
    public boolean isQuoted() {
        return false;
    }

    @Override
    public @NotNull JBIterable<? extends DasObject> getDasChildren(@Nullable ObjectKind kind) {
        if (kind == ObjectKind.SCHEMA) {
            return JBIterable.from(mySchemas);
        }
        return JBIterable.empty();
    }
}
