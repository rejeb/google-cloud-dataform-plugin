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
package io.github.rejeb.dataform.language.folding;

import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.psi.PsiElement;
import io.github.rejeb.dataform.language.evaluation.DataformExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Builds the one-line fold descriptors shared by the SQLX and JavaScript folding builders, so the
 * placeholder and grouping rules live in a single place.
 */
public final class DataformFoldDescriptors {

    private DataformFoldDescriptors() {
    }

    /**
     * Builds the one-line value descriptor of an expression, or {@code null} when there is no value
     * or the placeholder would add nothing over the source text.
     */
    @Nullable
    public static FoldingDescriptor of(@NotNull PsiElement element,
                                       @NotNull DataformExpression expression,
                                       @Nullable String value,
                                       boolean grouped) {
        if (value == null) {
            return null;
        }
        String placeholder = DataformFoldingPlaceholder.of(value, expression.hostText());
        if (placeholder == null) {
            return null;
        }
        return new FoldingDescriptor(element.getNode(), expression.hostRange(),
                grouped ? DataformFoldingPlaceholder.newGroup() : null, placeholder);
    }
}
