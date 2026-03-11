package io.github.rejeb.dataform.language.schema.sql;

import com.intellij.database.model.ObjectKind;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.ResolveState;
import com.intellij.sql.psi.SqlReference;
import com.intellij.sql.psi.SqlScopeProcessor;
import com.intellij.sql.psi.impl.SqlResolveExtension;
import io.github.rejeb.dataform.language.schema.sql.model.DataformDasCatalog;
import io.github.rejeb.dataform.language.schema.sql.model.DataformDasColumn;
import io.github.rejeb.dataform.language.schema.sql.model.DataformDasSchema;
import io.github.rejeb.dataform.language.schema.sql.model.DataformDasTable;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class DataformSqlResolveExtension implements SqlResolveExtension {

    @Override
    public boolean process(@NotNull SqlReference ref,
                           @NotNull SqlScopeProcessor processor) {

        PsiElement place = processor.getPlace();
        if (place == null) return true;

        PsiFile topLevel = InjectedLanguageManager
                .getInstance(place.getProject())
                .getTopLevelFile(place.getContainingFile());
        if (topLevel == null || !topLevel.getName().endsWith(".sqlx")) return true;

        if (!processor.mayAccept(ObjectKind.TABLE)
                && !processor.mayAccept(ObjectKind.COLUMN)) return true;

        DataformTableSchemaService svc = place.getProject()
                .getService(DataformTableSchemaService.class);
        Map<String, List<ColumnInfo>> cache = svc.getAllSchemas();
        if (cache.isEmpty()) return true;

        String refName = ref.getReferenceName();

        if (processor.mayAccept(ObjectKind.DATABASE)) {
            Map<String, Map<String, List<Map.Entry<String, List<ColumnInfo>>>>> tree
                    = buildTree(cache);

            for (Map.Entry<String, Map<String, List<Map.Entry<String, List<ColumnInfo>>>>> db : tree.entrySet()) {
                if (db.getKey().equalsIgnoreCase(refName)) {
                    DataformDasCatalog dataformDasCatalog = new DataformDasCatalog(db.getKey(), db.getValue());
                    if (!processor.executeObject(dataformDasCatalog, null, null,
                            ResolveState.initial())) return false;
                }
            }
        }

        if (processor.mayAccept(ObjectKind.SCHEMA)) {
            Map<String, Map<String, List<Map.Entry<String, List<ColumnInfo>>>>> tree
                    = buildTree(cache);

            for (Map.Entry<String, Map<String, List<Map.Entry<String, List<ColumnInfo>>>>> db : tree.entrySet()) {
                DataformDasCatalog dataformDasCatalog = new DataformDasCatalog(db.getKey(), db.getValue());
                for (Map.Entry<String, List<Map.Entry<String, List<ColumnInfo>>>> schema : db.getValue().entrySet())
                    if (schema.getKey().equalsIgnoreCase(refName)) {
                        DataformDasSchema dataformDasSchema = new DataformDasSchema(dataformDasCatalog, schema.getKey(), schema.getValue());
                        if (!processor.executeObject(dataformDasSchema, null, null,
                                ResolveState.initial())) return false;
                    }
            }
        }

        for (Map.Entry<String, List<ColumnInfo>> entry : cache.entrySet()) {
            String[] parts = entry.getKey().split("\\.", 3);
            if (parts.length != 3) continue;

            String tableName = parts[2];

            if (processor.mayAccept(ObjectKind.TABLE)
                    && tableName.equalsIgnoreCase(refName)) {
                DataformDasCatalog dataformDasCatalog = new DataformDasCatalog(parts[0], new HashMap<>());
                DataformDasSchema dataformDasSchema = new DataformDasSchema(dataformDasCatalog, parts[1], List.of());
                DataformDasTable table = new DataformDasTable(
                        dataformDasSchema, tableName, entry.getValue());
                if (!processor.executeObject(table, null, null,
                        ResolveState.initial())) return false;
            }

            if (processor.mayAccept(ObjectKind.COLUMN)) {
                DataformDasTable table = new DataformDasTable(
                        null, tableName, entry.getValue());
                for (ColumnInfo col : entry.getValue()) {
                    if (col.name().startsWith(refName)) {
                        DataformDasColumn column = new DataformDasColumn(table, col);
                        if (!processor.executeObject(column, null, null,
                                ResolveState.initial())) return false;
                    }
                }
            }
        }
        return true;
    }

    private Map<String, Map<String, List<Map.Entry<String, List<ColumnInfo>>>>> buildTree(Map<String, List<ColumnInfo>> cache) {
        Map<String, Map<String, List<Map.Entry<String, List<ColumnInfo>>>>> tree
                = new LinkedHashMap<>();

        for (Map.Entry<String, List<ColumnInfo>> entry : cache.entrySet()) {
            String[] parts = entry.getKey().split("\\.", 3);
            if (parts.length != 3) continue;
            tree.computeIfAbsent(parts[0], k -> new LinkedHashMap<>())
                    .computeIfAbsent(parts[1], k -> new ArrayList<>())
                    .add(entry);
        }

        return tree;
    }
}
