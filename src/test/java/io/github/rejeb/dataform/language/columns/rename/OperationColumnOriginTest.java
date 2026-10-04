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
package io.github.rejeb.dataform.language.columns.rename;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.sql.psi.SqlCompositeElementTypes;
import io.github.rejeb.dataform.language.injection.InjectedFiles;
import io.github.rejeb.dataform.language.columns.model.ColumnRef;
import io.github.rejeb.dataform.language.columns.origin.ColumnOriginService;
import io.github.rejeb.dataform.language.schema.sql.SqlPsiParts;

/**
 * An operations action with {@code hasOutput} builds a table as a table action does, so the columns
 * its {@code CREATE TABLE ... AS SELECT} names are declared there.
 */
public class OperationColumnOriginTest extends ColumnRenameFixture {

    private PsiFile operationFile() {
        installProject();
        addOperation("ops");
        return addFile("ops", "config { type: \"operations\", hasOutput: true }\n\n"
                + "CREATE OR REPLACE TABLE `p.d.ops` AS\nSELECT 1 AS customer_id\n");
    }

    private PsiElement aliasIn(PsiFile hostFile) {
        for (PsiFile injected : InjectedFiles.mainSql(hostFile)) {
            for (PsiElement element : PsiTreeUtil.collectElements(injected,
                    candidate -> SqlPsiParts.isType(candidate, SqlCompositeElementTypes.SQL_AS_EXPRESSION))) {
                return element;
            }
        }
        return null;
    }

    public void testTheSelectListOfAnOperationWithOutputDeclaresItsColumns() {
        PsiFile file = operationFile();
        PsiElement alias = aliasIn(file);
        assertNotNull(alias);

        assertEquals(new ColumnRef("p.d.ops", "customer_id"),
                ColumnOriginService.getInstance(getProject()).declaredColumn(file, alias));
    }

    public void testAColumnOfAnOperationWithOutputIsDeclaredInItsFile() {
        operationFile();

        PsiElement declaring = ColumnOriginService.getInstance(getProject())
                .declaringElement(new ColumnRef("p.d.ops", "customer_id"));

        assertNotNull("the column is declared in the operation's select list", declaring);
        assertEquals("customer_id", declaring.getText());
    }
}
