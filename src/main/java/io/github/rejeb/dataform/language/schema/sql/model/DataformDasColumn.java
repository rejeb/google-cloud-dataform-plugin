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
package io.github.rejeb.dataform.language.schema.sql.model;

import com.intellij.database.Dbms;
import com.intellij.database.model.DasColumn;
import com.intellij.database.model.DasObject;
import com.intellij.database.model.DasTable;
import com.intellij.database.model.ObjectKind;
import com.intellij.database.symbols.DasSymbol;
import com.intellij.database.types.DasType;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.sql.dialects.bigquery.BigQueryDialect;
import com.intellij.sql.psi.SqlCompositeElementTypes;
import com.intellij.util.containers.JBIterable;
import io.github.rejeb.dataform.language.psi.SqlxFile;
import io.github.rejeb.dataform.language.psi.SqlxSqlBlock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;


public class DataformDasColumn extends LightElement implements DasColumn, DasSymbol {
    private final DataformDasTable myParent;
    private final ColumnInfo myInfo;
    private final PsiFile containingFile;

    public DataformDasColumn(@NotNull PsiManager psiManager,
                             @Nullable DataformDasTable parent,
                             @NotNull ColumnInfo info,
                             PsiFile containingFile) {
        super(psiManager, BigQueryDialect.INSTANCE);
        this.myParent = parent;
        this.myInfo = info;
        this.containingFile = containingFile;
    }

    @Override
    public @NotNull String getName() {
        return myInfo.name();
    }

    @Override
    public @NotNull String toString() {
        return myInfo.name();
    }

    @Override
    public @Nullable DasObject getDasParent() {
        return myParent;
    }

    @Override
    public @NotNull Dbms getDbms() {
        return Dbms.BIGQUERY;
    }

    @Override
    public @Nullable DasObject getDasObject() {
        return this;
    }

    @Override
    public @NotNull ObjectKind getKind() {
        return ObjectKind.COLUMN;
    }

    @Override
    public @NotNull JBIterable<? extends PsiElement> getPsiDeclarations() {
        return JBIterable.of(this);
    }

    @Override
    public @Nullable PsiElement getContextElement() {
        return this;
    }

    @Override
    public PsiFile getContainingFile() {
        return containingFile;
    }

    @Override
    public ItemPresentation getPresentation() {
        return new ItemPresentation() {
            @Override
            public String getPresentableText() {
                return myInfo.name();
            }

            @Override
            public String getLocationString() {
                return myParent != null ? myParent.getName() : null;
            }

            @Override
            public javax.swing.Icon getIcon(boolean unused) {
                return null;
            }
        };
    }

    @Override
    public boolean isQuoted() {
        return false;
    }

    @Override
    public boolean isNotNull() {
        return "REQUIRED".equals(myInfo.mode());
    }

    @Override
    public @Nullable String getDefault() {
        return null;
    }

    @Override
    public @NotNull DasType getDasType() {
        return myInfo.dasType();
    }

    @Override
    public short getPosition() {
        return 0;
    }

    @Override
    public @Nullable DasTable getTable() {
        return myParent;
    }

    @Override
    public void navigate(boolean requestFocus) {
        if (containingFile == null || containingFile.getVirtualFile() == null) return;
        int offset = findColumnOffsetInSqlBlock();
        if (offset >= 0) {
            new OpenFileDescriptor(getProject(), containingFile.getVirtualFile(), offset).navigate(requestFocus);
        } else {
            new OpenFileDescriptor(getProject(), containingFile.getVirtualFile()).navigate(requestFocus);
        }
    }

    private int findColumnOffsetInSqlBlock() {
        if (!(containingFile instanceof SqlxFile)) return -1;
        SqlxSqlBlock sqlBlock = PsiTreeUtil.findChildOfType(containingFile, SqlxSqlBlock.class);
        if (sqlBlock == null) return -1;
        InjectedLanguageManager ilm = InjectedLanguageManager.getInstance(containingFile.getProject());
        List<Pair<PsiElement, TextRange>> injectedFiles = ilm.getInjectedPsiFiles(sqlBlock);
        if (injectedFiles == null || injectedFiles.isEmpty()) return -1;
        for (Pair<PsiElement, TextRange> pair : injectedFiles) {
            PsiFile injectedSql = pair.getFirst().getContainingFile();
            PsiElement[] asExpressions = PsiTreeUtil.collectElements(
                    injectedSql,
                    e -> e.getNode().getElementType() == SqlCompositeElementTypes.SQL_AS_EXPRESSION);
            for (PsiElement asExpr : asExpressions) {
                PsiElement aliasId = findLastIdentifier(asExpr);
                if (aliasId != null && identifierMatches(aliasId, myInfo.name())) {
                    return ilm.injectedToHost(aliasId, aliasId.getTextOffset());
                }
            }
            PsiElement[] colRefs = PsiTreeUtil.collectElements(
                    injectedSql,
                    e -> e.getNode().getElementType() == SqlCompositeElementTypes.SQL_COLUMN_REFERENCE);
            for (PsiElement colRef : colRefs) {
                PsiElement nameId = findLastIdentifier(colRef);
                if (nameId != null && identifierMatches(nameId, myInfo.name())) {
                    return ilm.injectedToHost(nameId, nameId.getTextOffset());
                }
            }
        }
        return -1;
    }

    private static PsiElement findLastIdentifier(@NotNull PsiElement parent) {
        PsiElement last = null;
        for (PsiElement child : parent.getChildren()) {
            if (child.getNode().getElementType() == SqlCompositeElementTypes.SQL_IDENTIFIER) {
                last = child;
            }
        }
        return last;
    }

    private static boolean identifierMatches(@NotNull PsiElement identifier, @NotNull String name) {
        return identifier.getText().replace("`", "").equalsIgnoreCase(name);
    }

    @Override
    public boolean canNavigate() {
        return containingFile != null && containingFile.getVirtualFile() != null;
    }

    @Override
    public boolean canNavigateToSource() {
        return canNavigate();
    }
}
