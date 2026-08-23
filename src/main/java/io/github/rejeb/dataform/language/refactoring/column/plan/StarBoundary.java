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
package io.github.rejeb.dataform.language.refactoring.column.plan;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPsiElementPointer;
import io.github.rejeb.dataform.language.lineage.column.ColumnRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A column of the rename that has no declaration to rewrite, because the action building it selects
 * a star.
 *
 * @param column   the column with no renameable declaration
 * @param file     the file of the action building it
 * @param star     the star element, absent when the file itself could not be located
 * @param blockers why the star cannot be expanded, empty when it can
 */
public record StarBoundary(@NotNull ColumnRef column,
                           @Nullable VirtualFile file,
                           @Nullable SmartPsiElementPointer<PsiElement> star,
                           @NotNull List<String> blockers) {

    /** Whether expanding the star into an explicit column list is possible here. */
    public boolean canExpand() {
        return star != null && blockers.isEmpty();
    }
}
