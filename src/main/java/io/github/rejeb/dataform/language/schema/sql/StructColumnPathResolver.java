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
import io.github.rejeb.dataform.language.schema.sql.model.StructColumnPath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Reads the path a caret sits on inside a qualified column reference.
 *
 * <p>Only the first segments of {@code n.customer.origin.code} belong to the schema: the platform
 * resolves {@code n.customer} to a column of a Dataform table and everything past it to a field of
 * that column's type, which lives in a throwaway file of its own. Walking the reference from the
 * outside in is what tells the two apart.</p>
 *
 * <p>Answers from the last compilation and never triggers one.</p>
 */
public interface StructColumnPathResolver {

    static StructColumnPathResolver getInstance(@NotNull Project project) {
        return project.getService(StructColumnPathResolver.class);
    }

    /**
     * The column, and the fields walked into it, the token sits on. {@code null} when the token is
     * not part of a column reference, or names something the schema does not hold.
     */
    @Nullable
    StructColumnPath pathAt(@NotNull PsiElement token);

    /**
     * The field a declaration names, when the token is the alias of a field written inside a
     * {@code STRUCT(...)} of the select list. {@code null} for anything else, a column of the table
     * included: that is a column the schema holds rather than a field of one.
     *
     * <p>The inverse of the walk navigation does. A field is declared in the action building its
     * column, and the reader pointing at that declaration is asking the same question as one pointing
     * at a read of it, so both have to arrive at the same path.</p>
     */
    @Nullable
    StructColumnPath declaredPathAt(@NotNull PsiElement token);

    /**
     * The reference element the token's own segment belongs to, which is the occurrence a window
     * opened on this token stands on. A reference handed in stands for its own last segment, so a
     * caller already holding one may ask with it. {@code null} when the token names no segment.
     */
    @Nullable
    PsiElement segmentAt(@NotNull PsiElement token);
}
