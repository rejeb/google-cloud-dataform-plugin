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
import com.intellij.sql.psi.SqlCompositeElementTypes;
import com.intellij.sql.psi.SqlReference;
import com.intellij.sql.psi.SqlScopeProcessor;
import com.intellij.sql.psi.impl.SqlResolveExtension;
import com.intellij.sql.symbols.DasSymbolUtil;
import io.github.rejeb.dataform.language.schema.sql.model.DataformDasTable;
import org.jetbrains.annotations.NotNull;

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

        if (ref.getReferenceElementType() != SqlCompositeElementTypes.SQL_TABLE_REFERENCE) return true;
        if (!processor.mayAccept(ObjectKind.TABLE)) return true;

        String refName = ref.getReferenceName();

        Map<String, DataformDasTable> tables = DataformTableSchemaService
                .getInstance(place.getProject())
                .getAllTables();
        if (tables.isEmpty()) return true;

        for (DataformDasTable table : tables.values()) {
            if (!table.getName().equalsIgnoreCase(refName)) continue;
            DasSymbol tableSymbol = DasSymbolUtil.wrapObjectToSymbol(table, processor);
            if (!processor.execute(tableSymbol, ResolveState.initial())) return false;
        }
        return true;
    }
}
