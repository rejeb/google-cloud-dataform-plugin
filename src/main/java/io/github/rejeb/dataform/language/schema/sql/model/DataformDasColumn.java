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

import com.intellij.database.model.DasColumn;
import com.intellij.database.model.DasObject;
import com.intellij.database.model.DasTable;
import com.intellij.database.model.ObjectKind;
import com.intellij.database.types.DasType;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.sql.psi.SqlCompositeElementTypes;
import io.github.rejeb.dataform.language.columns.model.ColumnRef;
import io.github.rejeb.dataform.language.columns.origin.SqlxOutputColumnLocator;
import io.github.rejeb.dataform.language.injection.InjectedFiles;
import io.github.rejeb.dataform.language.psi.SqlxFile;
import io.github.rejeb.dataform.language.psi.SqlxSqlBlock;
import io.github.rejeb.dataform.language.schema.sql.SqlPsiParts;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class DataformDasColumn extends DataformDasElement implements DasColumn {
    private final DataformDasTable myParent;
    private final ColumnInfo myInfo;
    private final PsiFile containingFile;

    public DataformDasColumn(@NotNull PsiManager psiManager,
                             @Nullable DataformDasTable parent,
                             @NotNull ColumnInfo info,
                             PsiFile containingFile) {
        super(psiManager);
        this.myParent = parent;
        this.myInfo = info;
        this.containingFile = containingFile;
    }

    @Override
    public @NotNull String getName() {
        return myInfo.name();
    }

    /**
     * Returns the schema information backing this column.
     */
    public @NotNull ColumnInfo getColumnInfo() {
        return myInfo;
    }

    /**
     * Two instances describing the same column of the same table are the same declaration. A new
     * instance is created on every resolve, so reference matching (Find Usages, highlighting)
     * must compare the logical identity, not the instance.
     */
    @Override
    public boolean isEquivalentTo(PsiElement another) {
        if (this == another) return true;
        if (!(another instanceof DataformDasColumn other)) return false;
        if (!myInfo.name().equalsIgnoreCase(other.myInfo.name())) return false;
        if (myParent != null || other.myParent != null) {
            return myParent != null && myParent.isEquivalentTo(other.myParent);
        }
        return containingFile != null && containingFile.equals(other.containingFile);
    }

    @Override
    public @Nullable DasObject getDasParent() {
        return myParent;
    }

    @Override
    public @NotNull ObjectKind getKind() {
        return ObjectKind.COLUMN;
    }

    @Override
    public PsiFile getContainingFile() {
        return containingFile;
    }

    /**
     * Always valid. The platform rejects any resolve result that is not, and the SQL support hands
     * back columns it made earlier from its own caches, also after the file a column was read from
     * has been deleted: such a column stays usable, it only has nowhere to navigate to. The
     * inherited check searched the file for the declaration on every call, which fails on a file
     * no longer valid.
     */
    @Override
    public boolean isValid() {
        return true;
    }

    private boolean hasLiveFile() {
        return containingFile != null && containingFile.isValid();
    }

    /**
     * The select-list element declaring this column, so navigation lands on real source rather
     * than on this synthetic element. Falls back to itself when the file does not declare it.
     */
    @Override
    public @NotNull PsiElement getNavigationElement() {
        if (!hasLiveFile()) return this;
        PsiElement declaration =
                SqlxOutputColumnLocator.findOutputColumn(containingFile, myInfo.name());
        return declaration != null ? declaration : this;
    }

    /**
     * No location. SQL completion qualifies an insert as {@code table.column} whenever a column
     * offers one, so a column of the query's own tables has to offer none to be inserted under its
     * bare name.
     */
    @Override
    protected @Nullable String presentableLocation() {
        return null;
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

    /**
     * The column as the rest of the plugin names it, whether or not its table is still published.
     *
     * @return the full name of the table and the column name, {@code null} when the table's full name is unknown
     */
    public @Nullable ColumnRef ref() {
        return getTable() instanceof DataformDasTable table && table.getFullName() != null
                ? new ColumnRef(table.getFullName(), getName()) : null;
    }

    @Override
    public @Nullable DasTable getTable() {
        return myParent;
    }

    @Override
    public void navigate(boolean requestFocus) {
        if (!hasLiveFile() || containingFile.getVirtualFile() == null) return;
        int offset = findColumnOffsetInSqlBlock();
        if (offset >= 0) {
            new OpenFileDescriptor(getProject(), containingFile.getVirtualFile(), offset).navigate(requestFocus);
        } else {
            new OpenFileDescriptor(getProject(), containingFile.getVirtualFile()).navigate(requestFocus);
        }
    }

    private int findColumnOffsetInSqlBlock() {
        if (!(containingFile instanceof SqlxFile)) return -1;
        InjectedLanguageManager ilm = InjectedLanguageManager.getInstance(containingFile.getProject());
        PsiElement declaration =
                SqlxOutputColumnLocator.findOutputColumn(containingFile, myInfo.name());
        if (declaration != null) {
            return ilm.injectedToHost(declaration, declaration.getTextOffset());
        }
        return findFirstMentionInSqlBlock(ilm);
    }

    private int findFirstMentionInSqlBlock(@NotNull InjectedLanguageManager ilm) {
        SqlxSqlBlock sqlBlock = PsiTreeUtil.findChildOfType(containingFile, SqlxSqlBlock.class);
        if (sqlBlock == null) return -1;
        for (PsiFile injectedSql : InjectedFiles.of(List.of(sqlBlock))) {
            for (IElementType type : List.of(SqlCompositeElementTypes.SQL_AS_EXPRESSION,
                    SqlCompositeElementTypes.SQL_COLUMN_REFERENCE)) {
                for (PsiElement element : SqlPsiParts.childrenOfTypeDeep(injectedSql, type)) {
                    PsiElement id = SqlPsiParts.lastIdentifier(element);
                    if (id != null && SqlPsiParts.unquoted(id.getText()).equalsIgnoreCase(myInfo.name())) {
                        return ilm.injectedToHost(id, id.getTextOffset());
                    }
                }
            }
        }
        return -1;
    }

    @Override
    public boolean canNavigate() {
        return hasLiveFile() && containingFile.getVirtualFile() != null;
    }
}
