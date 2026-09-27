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
package io.github.rejeb.dataform.language.unittest.navigation;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import io.github.rejeb.dataform.language.lineage.column.ColumnRef;
import io.github.rejeb.dataform.language.schema.sql.ColumnOriginService;
import io.github.rejeb.dataform.language.schema.sql.StructColumnPaths;
import io.github.rejeb.dataform.language.unittest.SqlxUnitTests;
import io.github.rejeb.dataform.language.unittest.columns.TestColumnAlias;
import io.github.rejeb.dataform.language.unittest.columns.TestColumnAliases;
import org.jetbrains.annotations.Nullable;

/**
 * Takes Ctrl+Click on an alias of a unit test to the declaration of the column it stands for: the
 * column of the table an input mocks, or of the tested dataset for the expected output.
 */
public final class TestAliasGotoDeclarationHandler implements GotoDeclarationHandler {

    @Override
    public PsiElement @Nullable [] getGotoDeclarationTargets(@Nullable PsiElement sourceElement,
                                                             int offset,
                                                             @Nullable Editor editor) {
        if (sourceElement == null || sourceElement.getContainingFile() == null) {
            return null;
        }
        Project project = sourceElement.getProject();
        PsiFile host = InjectedLanguageManager.getInstance(project).getTopLevelFile(sourceElement);
        if (host == null || !SqlxUnitTests.isUnitTestFile(host)) {
            return null;
        }
        TestColumnAlias alias = TestColumnAliases.getInstance(project).at(sourceElement).orElse(null);
        if (alias == null) {
            return null;
        }
        PsiElement declaration = declarationOf(project, alias.column());
        return declaration == null ? null : new PsiElement[]{declaration};
    }

    @Nullable
    private static PsiElement declarationOf(Project project, ColumnRef column) {
        ColumnOriginService origins = ColumnOriginService.getInstance(project);
        if (!column.columnName().contains(".")) {
            return origins.declaringElement(column);
        }
        return StructColumnPaths.of(project, column).map(origins::declaringElement).orElse(null);
    }
}
