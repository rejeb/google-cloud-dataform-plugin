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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DataformSqlResolveExtension implements SqlResolveExtension {

    @Override
    public boolean process(@NotNull SqlReference ref,
                           @NotNull SqlScopeProcessor processor) {

        // 1. Vérifier qu'on est dans un .sqlx (via injection)
        PsiElement place = processor.getPlace();
        if (place == null) return true;

        PsiFile topLevel = InjectedLanguageManager
                .getInstance(place.getProject())
                .getTopLevelFile(place.getContainingFile());
        if (topLevel == null || !topLevel.getName().endsWith(".sqlx")) return true;

        // 2. Vérifier que le kind attendu est compatible
        if (!processor.mayAccept(ObjectKind.TABLE)
                && !processor.mayAccept(ObjectKind.COLUMN)) return true;

        // 3. Récupérer le cache
        DataformTableSchemaService svc = place.getProject()
                .getService(DataformTableSchemaService.class);
        Map<String, List<ColumnInfo>> cache = svc.getAllSchemas();
        if (cache.isEmpty()) return true;

        String refName = ref.getReferenceName();

        // 4. Injecter les DasObject
        for (Map.Entry<String, List<ColumnInfo>> entry : cache.entrySet()) {
            String[] parts = entry.getKey().split("\\.", 3);
            if (parts.length != 3) continue;

            String tableName = parts[2];

            // Résolution de table
            if (processor.mayAccept(ObjectKind.TABLE)
                    && tableName.equalsIgnoreCase(refName)) {
                DataformDasCatalog dataformDasCatalog = new DataformDasCatalog(parts [0],new HashMap<>());
                DataformDasSchema dataformDasSchema = new DataformDasSchema(dataformDasCatalog,parts[1],List.of());
                DataformDasTable table = new DataformDasTable(
                        dataformDasSchema, tableName, List.of());
                if (!processor.executeObject(table, null, null,
                        ResolveState.initial())) return false;
            }

            // Résolution de colonne (ref sans qualificateur)
            if (processor.mayAccept(ObjectKind.COLUMN)) {
                for (ColumnInfo col : entry.getValue()) {
                    if (col.name().startsWith(refName)) {
                       DataformDasTable table = new DataformDasTable(
                                null, tableName, entry.getValue());
                        DataformDasColumn column = new DataformDasColumn(table, col);
                        if (!processor.executeObject(column, null, null,
                                ResolveState.initial())) return false;
                    }
                }
            }
        }
        return true;
    }
}
