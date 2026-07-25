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
import com.intellij.psi.tree.IElementType;
import com.intellij.sql.psi.SqlCompositeElementTypes;
import com.intellij.sql.psi.SqlReference;
import com.intellij.sql.psi.SqlScopeProcessor;
import com.intellij.sql.psi.impl.SqlResolveExtension;
import com.intellij.sql.symbols.DasSymbolUtil;
import io.github.rejeb.dataform.language.schema.sql.model.ColumnInfo;
import io.github.rejeb.dataform.language.schema.sql.model.DataformDasColumn;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Resolves references to BigQuery scripting variables declared with {@code DECLARE} in a SQLX
 * {@code pre_operations} or {@code post_operations} block. Those blocks are injected as separate
 * SQL documents, so the main query cannot see the declaration through the SQL scope on its own.
 */
public class DataformSqlVariableResolveExtension implements SqlResolveExtension {

    @Override
    public boolean process(@NotNull SqlReference ref,
                           @NotNull SqlScopeProcessor processor) {
        PsiElement place = processor.getPlace();
        if (place == null) {
            return true;
        }

        PsiFile topLevel = InjectedLanguageManager
                .getInstance(place.getProject())
                .getTopLevelFile(place.getContainingFile());
        if (topLevel == null || !topLevel.getName().endsWith(".sqlx")) {
            return true;
        }

        IElementType type = ref.getReferenceElementType();
        if (type != SqlCompositeElementTypes.SQL_COLUMN_REFERENCE
                && type != SqlCompositeElementTypes.SQL_COLUMN_SHORT_REFERENCE
                && type != SqlCompositeElementTypes.SQL_REFERENCE) {
            return true;
        }
        if (!processor.mayAccept(ObjectKind.COLUMN)) {
            return true;
        }

        Map<String, ColumnInfo> variables = DataformDeclaredVariablesScanner.scan(topLevel);
        if (variables.isEmpty()) {
            return true;
        }

        for (ColumnInfo info : variables.values()) {
            DataformDasColumn variable =
                    new DataformDasColumn(place.getManager(), null, info, topLevel);
            DasSymbol symbol = DasSymbolUtil.wrapObjectToSymbol(variable, processor);
            if (!processor.execute(symbol, ResolveState.initial())) {
                return false;
            }
        }
        return true;
    }
}
