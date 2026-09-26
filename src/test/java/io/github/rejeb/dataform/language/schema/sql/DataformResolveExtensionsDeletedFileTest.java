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

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.sql.psi.SqlReferenceExpression;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

/**
 * A highlighting pass may still be resolving the references of a file the user has just deleted,
 * and the SQL support then hands back the columns it made while the file lived. The platform
 * rejects any resolve result that is not valid, so such a column must still be.
 */
public class DataformResolveExtensionsDeletedFileTest extends BasePlatformTestCase {

    public void testAVariableOfADeletedFileResolvesWithoutAnInvalidResult() throws Exception {
        PsiFile file = myFixture.addFileToProject("definitions/vars.sqlx",
                "config { type: \"table\" }\npre_operations {\nDECLARE limit_value INT64 DEFAULT 10;\n}\n"
                        + "SELECT limit_value AS c\n");
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        PsiElement injected = InjectedLanguageManager.getInstance(getProject())
                .findInjectedElementAt(file, file.getText().indexOf("limit_value AS c"));
        SqlReferenceExpression reference = PsiTreeUtil.getParentOfType(injected, SqlReferenceExpression.class);
        assertNotNull("the variable resolves while its file lives", reference.resolve());

        WriteAction.runAndWait(() -> file.getVirtualFile().delete(this));

        ((PsiPolyVariantReference) reference.getReference()).multiResolve(false);
    }
}
