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

import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilderEx;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import io.github.rejeb.dataform.language.evaluation.DataformExpression;
import io.github.rejeb.dataform.language.settings.DataformToolsSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A folding builder showing Dataform expressions as their value, collapsed from the start.
 */
abstract class DataformValueFoldingBuilder extends FoldingBuilderEx {

    @Override
    public final @Nullable String getPlaceholderText(@NotNull ASTNode node) {
        return null;
    }

    @Override
    public final boolean isCollapsedByDefault(@NotNull ASTNode node) {
        return true;
    }

    static boolean isDisabled(@NotNull Project project, boolean quick) {
        return quick || DumbService.isDumb(project) || !DataformToolsSettings.getInstance().isFoldTemplateExpressions();
    }

    /**
     * Adds the one-line value descriptor of an expression, unless there is no value or the
     * placeholder would add nothing over the source text.
     */
    static void addDescriptor(@NotNull List<FoldingDescriptor> descriptors, @NotNull PsiElement element,
                              @NotNull DataformExpression expression, @Nullable String value, boolean grouped) {
        String placeholder = value == null ? null : DataformFoldingPlaceholder.of(value, expression.hostText());
        if (placeholder != null) {
            descriptors.add(new FoldingDescriptor(element.getNode(), expression.hostRange(),
                    grouped ? DataformFoldingPlaceholder.newGroup() : null, placeholder));
        }
    }
}
