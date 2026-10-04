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
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiManager;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public class DataformDasTable extends DataformDasElement implements DasTable {
    @Nullable
    private final String myFullName;
    private final String myName;
    private final List<ColumnInfo> myColumns;
    @Nullable
    private final VirtualFile mySourceFile;
    private volatile PsiFile myFallbackFile;

    public DataformDasTable(@NotNull PsiManager psiManager,
                            @NotNull String table,
                            @NotNull List<ColumnInfo> columns,
                            @Nullable VirtualFile sourceFile) {
        this(psiManager, null, table, columns, sourceFile);
    }

    /**
     * A table known by its full {@code database.schema.name}, which tells it apart from a table of
     * the same name in another dataset.
     */
    public DataformDasTable(@NotNull PsiManager psiManager,
                            @Nullable String fullName,
                            @NotNull String table,
                            @NotNull List<ColumnInfo> columns,
                            @Nullable VirtualFile sourceFile) {
        super(psiManager);
        this.myFullName = fullName;
        this.myName = table;
        this.myColumns = columns;
        this.mySourceFile = sourceFile;
    }

    /** The full {@code database.schema.name} of the table, {@code null} when unknown. */
    public @Nullable String getFullName() {
        return myFullName;
    }

    /**
     * Whether the table lives in a dataset. A table whose full name is unknown may live in any.
     *
     * @param schema the dataset name, compared without regard to case
     * @return whether the table lives in it
     */
    public boolean isInSchema(@NotNull String schema) {
        if (myFullName == null) return true;
        String[] parts = myFullName.split("\\.");
        return parts.length >= 2 && parts[parts.length - 2].equalsIgnoreCase(schema);
    }

    @Override
    public @NotNull String getName() {
        return myName;
    }

    public @NotNull List<ColumnInfo> getColumns() {
        return myColumns;
    }

    /**
     * The SQLX file the action building this table is declared in, as the file system has it now,
     * {@code null} when unknown or deleted.
     */
    public @Nullable VirtualFile getSourceFile() {
        return liveSourceFile();
    }

    /**
     * The source file the table was built with while it still exists, else the file now at its
     * path. A table outlives the schema refresh it was built by, and its file may be deleted or
     * replaced in the meantime, as a checkout does: the file object it holds then stands for
     * nothing, and handing it to the platform throws.
     */
    private @Nullable VirtualFile liveSourceFile() {
        VirtualFile file = mySourceFile;
        if (file == null || file.isValid()) return file;
        VirtualFile current = VirtualFileManager.getInstance().findFileByUrl(file.getUrl());
        return current != null && current.isValid() ? current : null;
    }

    /**
     * Two instances describing the same Dataform table are the same declaration. Instances are
     * rebuilt on every schema refresh, so reference matching (Find Usages, highlighting) must
     * compare the logical identity, not the instance.
     */
    @Override
    public boolean isEquivalentTo(PsiElement another) {
        if (this == another) return true;
        if (!(another instanceof DataformDasTable other)) return false;
        if (myFullName != null && other.myFullName != null) {
            return myFullName.equalsIgnoreCase(other.myFullName);
        }
        return myName.equalsIgnoreCase(other.myName)
                && Objects.equals(mySourceFile, other.mySourceFile);
    }

    @Override
    public @Nullable DasObject getDasParent() {
        return null;
    }

    @Override
    public @NotNull ObjectKind getKind() {
        return ObjectKind.TABLE;
    }

    @Override
    public PsiFile getContainingFile() {
        VirtualFile source = liveSourceFile();
        if (source != null) {
            PsiFile file = getManager().findFile(source);
            if (file != null) return file;
        }
        PsiFile fallback = myFallbackFile;
        if (fallback == null || !fallback.isValid()) {
            fallback = PsiFileFactory.getInstance(getProject())
                    .createFileFromText("_dataform.txt", PlainTextFileType.INSTANCE, "");
            myFallbackFile = fallback;
        }
        return fallback;
    }

    @Override
    protected @Nullable String presentableLocation() {
        return mySourceFile != null ? mySourceFile.getName() : null;
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
                    .map(col -> new DataformDasColumn(getManager(), this, col, getContainingFile()));
        }
        return JBIterable.empty();
    }

    @Override
    public void navigate(boolean requestFocus) {
        VirtualFile source = liveSourceFile();
        if (source != null) {
            new OpenFileDescriptor(getProject(), source).navigate(requestFocus);
        }
    }

    @Override
    public boolean canNavigate() {
        return liveSourceFile() != null;
    }
}
