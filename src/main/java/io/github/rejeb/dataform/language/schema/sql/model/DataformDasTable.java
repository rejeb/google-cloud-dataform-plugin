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
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.sql.dialects.bigquery.BigQueryDialect;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

public class DataformDasTable extends LightElement implements DasTable, DasSymbol {
    private final String myName;
    private final List<ColumnInfo> myColumns;
    @Nullable
    private final VirtualFile mySourceFile;

    public DataformDasTable(@NotNull PsiManager psiManager,
                            @NotNull String table,
                            @NotNull List<ColumnInfo> columns,
                            @Nullable VirtualFile sourceFile) {
        super(psiManager, BigQueryDialect.INSTANCE);
        this.myName = table;
        this.myColumns = columns;
        this.mySourceFile = sourceFile;
    }

    @Override
    public @NotNull String getName() {
        return myName;
    }

    public @NotNull List<ColumnInfo> getColumns() {
        return myColumns;
    }

    @Override
    public @NotNull String toString() {
        return myName;
    }

    @Override
    public @Nullable DasObject getDasParent() {
        return null;
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
        return ObjectKind.TABLE;
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
        if (mySourceFile != null) {
            PsiFile file = getManager().findFile(mySourceFile);
            if (file != null) return file;
        }
        return PsiFileFactory.getInstance(getProject())
                .createFileFromText("_dataform.txt", PlainTextFileType.INSTANCE, "");
    }

    @Override
    public ItemPresentation getPresentation() {
        return new ItemPresentation() {
            @Override
            public String getPresentableText() {
                return myName;
            }

            @Override
            public String getLocationString() {
                return mySourceFile != null ? mySourceFile.getName() : null;
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
    public boolean isSystem() {
        return false;
    }

    @Override
    public boolean isTemporary() {
        return false;
    }

    @Override
    public @NotNull Set<DasColumn.Attribute> getColumnAttrs(@Nullable DasColumn columnInfo) {
        return Set.of();
    }

    @Override
    public @NotNull JBIterable<? extends DasObject> getDasChildren(@Nullable ObjectKind kind) {
        if (kind == ObjectKind.COLUMN) {
            return JBIterable.from(myColumns)
                    .map(col -> new DataformDasColumn(getManager(), null, col, getContainingFile()));
        }
        return JBIterable.empty();
    }

    @Override
    public void navigate(boolean requestFocus) {
        if (mySourceFile != null) {
            new OpenFileDescriptor(getProject(), mySourceFile).navigate(requestFocus);
        }
    }

    @Override
    public boolean canNavigate() {
        return mySourceFile != null;
    }

    @Override
    public boolean canNavigateToSource() {
        return canNavigate();
    }

}
