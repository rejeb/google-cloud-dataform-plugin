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
package io.github.rejeb.dataform.language.completion;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.lang.javascript.psi.JSArrayLiteralExpression;
import com.intellij.lang.javascript.psi.JSExpression;
import com.intellij.lang.javascript.psi.JSLiteralExpression;
import com.intellij.lang.javascript.psi.JSProperty;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import io.github.rejeb.dataform.language.compilation.DataformCompilationService;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.completion.config.ConfigSchemaLookup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * Completes the tags already used in the project when editing the {@code tags}
 * list of a SQLX config block.
 */
public class DataformConfigTagCompletionContributor extends CompletionContributor {

    private static final String TAGS_PROPERTY = "tags";

    public DataformConfigTagCompletionContributor() {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement(), new Provider());
    }

    private static final class Provider extends CompletionProvider<CompletionParameters> {

        @Override
        protected void addCompletions(@NotNull CompletionParameters parameters,
                                      @NotNull ProcessingContext context,
                                      @NotNull CompletionResultSet result) {
            PsiElement position = parameters.getPosition();
            if (!ConfigSchemaLookup.isInConfigBlock(position)) {
                return;
            }
            JSProperty property = tagsProperty(position);
            if (property == null) {
                return;
            }
            CompiledGraph graph = DataformCompilationService
                    .getInstance(position.getProject()).getCompiledGraph();
            if (graph == null) {
                return;
            }
            Set<String> tags = new TreeSet<>(graph.getTags());
            tags.removeAll(declaredTags(property, position));
            if (tags.isEmpty()) {
                return;
            }
            boolean quoted = PsiTreeUtil.getParentOfType(position, JSLiteralExpression.class, false)
                    != null;
            for (String tag : tags) {
                result.addElement(LookupElementBuilder
                        .create(quoted ? tag : "\"" + tag + "\"")
                        .withLookupString(tag)
                        .withPresentableText(tag)
                        .withTypeText(TAGS_PROPERTY)
                        .withIcon(AllIcons.Nodes.Tag));
            }
            result.stopHere();
        }

        @Nullable
        private static JSProperty tagsProperty(@NotNull PsiElement position) {
            JSProperty property = PsiTreeUtil.getParentOfType(position, JSProperty.class, false);
            if (property == null || !TAGS_PROPERTY.equals(property.getName())
                    || !ConfigSchemaLookup.isValuePosition(property, position)) {
                return null;
            }
            return PsiTreeUtil.getParentOfType(position, JSArrayLiteralExpression.class, false)
                    == null ? null : property;
        }

        private static Set<String> declaredTags(@NotNull JSProperty property,
                                                @NotNull PsiElement position) {
            JSArrayLiteralExpression array =
                    PsiTreeUtil.getParentOfType(position, JSArrayLiteralExpression.class, false);
            if (array == null) {
                return Set.of();
            }
            Set<String> declared = new HashSet<>();
            for (JSExpression expression : array.getExpressions()) {
                if (expression instanceof JSLiteralExpression literal
                        && literal.getValue() instanceof String tag
                        && !PsiTreeUtil.isAncestor(expression, position, false)) {
                    declared.add(tag);
                }
            }
            return declared;
        }
    }
}
