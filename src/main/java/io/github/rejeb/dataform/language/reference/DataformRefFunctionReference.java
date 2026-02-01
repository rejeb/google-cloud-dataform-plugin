/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
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
package io.github.rejeb.dataform.language.reference;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import io.github.rejeb.dataform.language.psi.SqlxFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.Objects;

public class DataformRefFunctionReference extends PsiReferenceBase<PsiElement> {

    private final String tableName;

    public DataformRefFunctionReference(@NotNull PsiElement element,
                                        @NotNull String tableName,
                                        @NotNull TextRange range) {
        super(element, range);
        this.tableName = tableName;
    }

    @Nullable
    @Override
    public PsiElement resolve() {
        String fileName = tableName + ".sqlx";
        Collection<VirtualFile> files = FilenameIndex.getVirtualFilesByName(
                fileName,
                GlobalSearchScope.projectScope(myElement.getProject())
        );
        PsiManager psiManager = PsiManager.getInstance(myElement.getProject());
        return files.stream()
                .map(psiManager::findFile)
                .filter(Objects::nonNull)
                .filter(file -> file instanceof SqlxFile)
                .findFirst()
                .orElse(null);
    }

    @Override
    public Object @NonNull [] getVariants() {

        Collection<VirtualFile> sqlxFiles = FilenameIndex.getAllFilesByExt(
                myElement.getProject(),
                "sqlx",
                GlobalSearchScope.projectScope(myElement.getProject())
        );

        return sqlxFiles.stream()
                .map(VirtualFile::getNameWithoutExtension)
                .toArray();
    }
}
