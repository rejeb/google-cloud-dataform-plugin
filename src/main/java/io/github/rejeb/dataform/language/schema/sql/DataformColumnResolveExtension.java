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

import com.intellij.codeInsight.completion.CompletionUtilCore;
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
import io.github.rejeb.dataform.language.lineage.column.ColumnRef;
import io.github.rejeb.dataform.language.schema.sql.model.DataformDasColumn;
import org.jetbrains.annotations.NotNull;

/**
 * Contributes the Dataform meaning of a column reference on top of the resolution the SQL plugin
 * performs, and never suppresses it: the extension always reports that processing continues, so a
 * reference that resolves today cannot stop resolving.
 *
 * <p>An item of the main select list produces a column of the table the file builds, which the SQL
 * plugin has no way of knowing: it resolves the item against the tables read by the query, not
 * against the table the query defines.</p>
 *
 * <p>Contributions are skipped while completion is collecting candidates. The reference name then
 * carries the dummy identifier and the targets below are about resolution, not completion; offering
 * them would list a column twice in the popup.</p>
 */
public class DataformColumnResolveExtension implements SqlResolveExtension {

    @Override
    public boolean process(@NotNull SqlReference ref, @NotNull SqlScopeProcessor processor) {
        PsiElement place = processor.getPlace();
        if (place == null) return true;

        PsiFile topLevel = InjectedLanguageManager.getInstance(place.getProject())
                .getTopLevelFile(place.getContainingFile());
        if (topLevel == null || !topLevel.getName().endsWith(".sqlx")) return true;

        IElementType type = ref.getReferenceElementType();
        if (type != SqlCompositeElementTypes.SQL_COLUMN_REFERENCE
                && type != SqlCompositeElementTypes.SQL_COLUMN_SHORT_REFERENCE) {
            return true;
        }
        if (!processor.mayAccept(ObjectKind.COLUMN)) return true;

        String refName = ref.getReferenceName();
        if (refName == null || refName.contains(CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED)) {
            return true;
        }
        if (!isSelectListItem(ref.getElement())) return true;

        return contributeDeclaredColumn(ref, processor, topLevel);
    }

    /**
     * Whether a reference is an item of a select list, decided from the tree alone.
     *
     * <p>Resolution runs this extension for every column reference in the file, and only an item of
     * a select list can declare a column. Everything the answer needs is the element's parent, so
     * that is checked before any service is asked anything: work done here is work done inside a
     * resolve, and a resolve that builds PSI or reads a graph can re-enter resolution and change
     * what an unrelated one returns.</p>
     */
    private static boolean isSelectListItem(@NotNull PsiElement element) {
        PsiElement parent = element.getParent();
        return parent != null
                && parent.getNode() != null
                && parent.getNode().getElementType() == SqlCompositeElementTypes.SQL_SELECT_CLAUSE;
    }

    private boolean contributeDeclaredColumn(@NotNull SqlReference ref,
                                             @NotNull SqlScopeProcessor processor,
                                             @NotNull PsiFile topLevel) {
        ColumnOriginService origins = ColumnOriginService.getInstance(topLevel.getProject());
        ColumnRef declared = origins.declaredColumn(topLevel, ref.getElement());
        if (declared == null) return true;
        DataformDasColumn column = origins.dasColumn(declared);
        if (column == null) return true;
        DasSymbol symbol = DasSymbolUtil.wrapObjectToSymbol(column, processor);
        return processor.execute(symbol, ResolveState.initial());
    }
}
