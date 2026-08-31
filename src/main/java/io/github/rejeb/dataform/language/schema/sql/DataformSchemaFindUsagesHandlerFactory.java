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

import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.find.findUsages.FindUsagesHandlerFactory;
import com.intellij.psi.PsiElement;
import io.github.rejeb.dataform.language.schema.sql.model.DataformDasColumn;
import io.github.rejeb.dataform.language.schema.sql.model.DataformDasTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Lets Find Usages run on the tables and columns of the Dataform schema, and on the aliases
 * declaring them.
 *
 * <p>A schema table and a schema column are synthetic elements built from the compiled graph rather
 * than SQL PSI, so the SQL plugin's own factory does not claim them and the action reports that
 * nothing can be searched. Reference search already finds their usages across files; only the
 * handler that lets the action reach that search is missing, and the default handler is exactly
 * it.</p>
 *
 * <p>An alias of the main select list is the other half of the same column. It is ordinary SQL PSI,
 * so the SQL plugin does claim it and the action runs — but it searches the alias alone, and the
 * files reading the table read the schema column instead. The alias keeps being searched, and the
 * column it declares is searched alongside it, which is what carries the action across files.</p>
 *
 * <p>The factory is registered ahead of the others because the SQL plugin claims the alias first,
 * and only the factory that claims an element decides what is searched for it. Everything that is
 * not a Dataform column is handed straight back, so the cost of running first is one element type
 * comparison.</p>
 */
public class DataformSchemaFindUsagesHandlerFactory extends FindUsagesHandlerFactory {

    @Override
    public boolean canFindUsages(@NotNull PsiElement element) {
        return element instanceof DataformDasColumn
                || element instanceof DataformDasTable
                || SqlxColumnAtCaret.declaredColumnOf(element) != null;
    }

    @Override
    public @Nullable FindUsagesHandler createFindUsagesHandler(@NotNull PsiElement element,
                                                               boolean forHighlightUsages) {
        if (element instanceof DataformDasColumn || element instanceof DataformDasTable) {
            return new FindUsagesHandler(element) {
            };
        }
        DataformDasColumn declared = SqlxColumnAtCaret.declaredColumnOf(element);
        if (declared == null) return null;
        return new FindUsagesHandler(element) {
            @Override
            public PsiElement @NotNull [] getSecondaryElements() {
                return new PsiElement[]{declared};
            }
        };
    }
}
