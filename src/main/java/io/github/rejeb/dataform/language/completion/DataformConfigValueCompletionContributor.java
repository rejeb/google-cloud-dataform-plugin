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

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.lang.javascript.psi.JSLiteralExpression;
import com.intellij.lang.javascript.psi.JSProperty;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import io.github.rejeb.dataform.language.completion.config.ConfigSchemaLookup;
import io.github.rejeb.dataform.language.completion.config.ConfigValueSkeletonBuilder;
import io.github.rejeb.dataform.language.completion.config.ConfigValueVariantInsertHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Completes the enumerated values allowed by the SQLX config schema for the
 * property being edited.
 */
public class DataformConfigValueCompletionContributor extends CompletionContributor {

    public DataformConfigValueCompletionContributor() {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement(), new Provider());
    }

    private static final class Provider extends CompletionProvider<CompletionParameters> {

        @Override
        protected void addCompletions(@NotNull CompletionParameters parameters,
                                      @NotNull ProcessingContext context,
                                      @NotNull CompletionResultSet result) {
            PsiElement position = parameters.getPosition();
            Optional<ConfigSchemaLookup> lookup = ConfigSchemaLookup.create(position);
            if (lookup.isEmpty()) {
                return;
            }
            Optional<ObjectNode> valueSchema = lookup.get().valueSchema(position);
            if (valueSchema.isEmpty()) {
                return;
            }
            boolean quoted = PsiTreeUtil.getParentOfType(position, JSLiteralExpression.class, false)
                    != null;
            if (!quoted && addVariants(lookup.get(), propertyNameAt(position), valueSchema.get(),
                    result)) {
                return;
            }
            List<String> values = lookup.get().enumValues(valueSchema.get());
            if (values.isEmpty()) {
                return;
            }
            for (String value : values) {
                LookupElement element = LookupElementBuilder
                        .create(quoted ? value : "\"" + value + "\"")
                        .withLookupString(value)
                        .withPresentableText(value)
                        .withIcon(AllIcons.Nodes.Enum);
                result.addElement(element);
            }
            result.stopHere();
        }

        /**
         * The name of the property whose value is being edited, null when the position sits in none.
         */
        @Nullable
        private static String propertyNameAt(@NotNull PsiElement position) {
            JSProperty property = PsiTreeUtil.getParentOfType(position, JSProperty.class, false);
            return property == null ? null : property.getName();
        }

        private static boolean addVariants(@NotNull ConfigSchemaLookup lookup,
                                           @Nullable String propertyName,
                                           @NotNull ObjectNode valueSchema,
                                           @NotNull CompletionResultSet result) {
            ConfigValueSkeletonBuilder builder = new ConfigValueSkeletonBuilder(lookup);
            List<ObjectNode> variants = builder.variantsOf(propertyName, valueSchema);
            if (variants.size() < 2) {
                return false;
            }
            for (ObjectNode variant : variants) {
                result.addElement(LookupElementBuilder.create(builder.variantLabel(variant))
                        .withIcon(AllIcons.Nodes.Type)
                        .withTypeText(variant.path("description").asText(""), true)
                        .withInsertHandler(new ConfigValueVariantInsertHandler(lookup, variant)));
            }
            result.stopHere();
            return true;
        }
    }
}
