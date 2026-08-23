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
package io.github.rejeb.dataform.language.refactoring.column.apply;

import com.intellij.psi.PsiElement;
import com.intellij.usageView.UsageViewDescriptor;
import org.jetbrains.annotations.NotNull;

/**
 * The headings of the rename window.
 */
public final class ColumnRenameViewDescriptor implements UsageViewDescriptor {

    private final PsiElement[] elements;
    private final String columnName;

    public ColumnRenameViewDescriptor(@NotNull PsiElement column, @NotNull String columnName) {
        this.elements = new PsiElement[]{column};
        this.columnName = columnName;
    }

    @Override
    public PsiElement @NotNull [] getElements() {
        return elements;
    }

    @Override
    public @NotNull String getProcessedElementsHeader() {
        return "Column to be renamed: " + columnName;
    }

    @Override
    public @NotNull String getCodeReferencesText(int usagesCount, int filesCount) {
        return "Places to rename (" + usagesCount + " in " + filesCount + " files)";
    }

    @Override
    public @NotNull String getCommentReferencesText(int usagesCount, int filesCount) {
        return "Occurrences in JavaScript and text (" + usagesCount + " in " + filesCount + " files)";
    }
}
