/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.rejeb.dataform.language.schema.sql;

import com.intellij.database.model.ObjectKind;
import com.intellij.database.symbols.DasSymbol;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.ResolveState;
import com.intellij.sql.psi.SqlReference;
import com.intellij.sql.psi.SqlScopeProcessor;
import com.intellij.sql.psi.impl.SqlResolveExtension;
import com.intellij.sql.symbols.DasSymbolUtil;
import io.github.rejeb.dataform.language.schema.sql.model.*;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

        DataformTableSchemaService svc = DataformTableSchemaService
                .getInstance(place.getProject());
        Map<String, List<ColumnInfo>> cache = svc.getAllSchemas();
        if (cache.isEmpty()) return true;

        String refName = ref.getReferenceName();

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
                DasSymbol tableSymbol = DasSymbolUtil.wrapObjectToSymbol(table, processor);
                if (!processor.execute(tableSymbol,
                        ResolveState.initial())) return false;
            }

            if (processor.mayAccept(ObjectKind.COLUMN)) {
                DataformDasTable table = new DataformDasTable(
                        null, tableName, entry.getValue());
                for (ColumnInfo col : entry.getValue()) {
                    if (col.name().startsWith(refName)) {
                        DataformDasColumn column = new DataformDasColumn(table, col);
                        DasSymbol columnSymbol = DasSymbolUtil.wrapObjectToSymbol(column, processor);
                        if (!processor.execute(columnSymbol,
                                ResolveState.initial())) return false;
                    }
                }
            }
        }
        return false;
    }

}
