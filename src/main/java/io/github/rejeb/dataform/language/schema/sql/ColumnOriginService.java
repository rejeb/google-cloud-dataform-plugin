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

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import io.github.rejeb.dataform.language.lineage.column.ColumnRef;
import io.github.rejeb.dataform.language.schema.sql.model.DataformDasColumn;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * What a select-list element means, and what a column relates to.
 *
 * <p>The roles are named rather than flattened into one list of targets. A resolve result is an
 * anonymous element, and a later rename has to tell the column an element declares from the column
 * it reads: it may rewrite the first and must not rewrite the second from this file.</p>
 *
 * <p>Every method answers from the last compilation and never triggers one.</p>
 */
public interface ColumnOriginService {

    static ColumnOriginService getInstance(@NotNull Project project) {
        return project.getService(ColumnOriginService.class);
    }

    /** The column this element declares, when it is an item of the main select list. */
    @Nullable
    ColumnRef declaredColumn(@NotNull PsiFile file, @NotNull PsiElement element);

    /** The element declaring a column, in the file of the action building it. */
    @Nullable
    PsiElement declaringElement(@NotNull ColumnRef column);

    /**
     * The element declaring a schema column, whichever way the column was built.
     *
     * <p>A column handed out by {@code DataformDasTable#getDasChildren} carries the table's
     * throwaway document rather than its source file, so its own navigation element is itself.
     * Going through the compiled graph answers the same question for every column alike.</p>
     */
    @Nullable
    PsiElement declaringElement(@NotNull DataformDasColumn column);

    /**
     * The columns feeding a column directly, empty when the lineage is unknown.
     *
     * <p>Reads the column lineage graph, which extracts and parses SQL when it is not already
     * built for the current compilation. Never call this from a resolve or on the EDT; the other
     * methods of this service read the schema alone and are safe there.</p>
     */
    @NotNull
    Set<ColumnRef> origins(@NotNull ColumnRef column);

    /** The schema column for a reference, or {@code null} when the schema does not have it. */
    @Nullable
    DataformDasColumn dasColumn(@NotNull ColumnRef column);
}
