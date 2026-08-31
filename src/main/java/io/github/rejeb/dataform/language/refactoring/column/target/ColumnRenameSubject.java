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
package io.github.rejeb.dataform.language.refactoring.column.target;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import io.github.rejeb.dataform.language.lineage.column.ColumnRef;
import org.jetbrains.annotations.NotNull;

/**
 * What a rename gesture is about: one logical Dataform column, and the identifier the caret sits on.
 *
 * <p>The caret shape decides which column is meant and nothing else. The scope of the rename comes
 * from the lineage graph, never from where the gesture started.</p>
 *
 * @param column     the column being renamed
 * @param identifier the identifier of the file the caret sits on, which anchors the template
 * @param hostFile   the SQLX file the caret is in
 * @param kind       what the caret sits on
 */
public record ColumnRenameSubject(@NotNull ColumnRef column,
                                  @NotNull PsiElement identifier,
                                  @NotNull PsiFile hostFile,
                                  @NotNull Kind kind) {

    /** What the identifier at the caret is, from the point of view of the column it names. */
    public enum Kind {
        /** The alias of an {@code AS} expression of the main select list. */
        SELECT_ALIAS,
        /** A bare item of the main select list, which declares the column and reads another. */
        SELECT_ITEM,
        /** An expression reading the column. */
        READ
    }

    /** Whether the file the caret is in declares the column being renamed. */
    public boolean declaresColumn() {
        return kind != Kind.READ;
    }

    /** The name the column carries today. */
    public @NotNull String oldName() {
        return column.columnName();
    }
}
