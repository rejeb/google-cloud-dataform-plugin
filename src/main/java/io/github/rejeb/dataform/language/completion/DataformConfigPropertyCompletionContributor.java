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
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.lang.javascript.psi.JSObjectLiteralExpression;
import com.intellij.lang.javascript.psi.JSProperty;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import io.github.rejeb.dataform.language.completion.config.ConfigPropertyInsertHandler;
import io.github.rejeb.dataform.language.completion.config.ConfigSchemaLookup;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Completes the property names allowed by the SQLX config schema, scoped to the
 * object currently being edited.
 */
public class DataformConfigPropertyCompletionContributor extends CompletionContributor {

    public DataformConfigPropertyCompletionContributor() {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement(), new Provider());
    }

    private static final class Provider extends CompletionProvider<CompletionParameters> {

        @Override
        protected void addCompletions(@NotNull CompletionParameters parameters,
                                      @NotNull ProcessingContext context,
                                      @NotNull CompletionResultSet result) {
            PsiElement position = parameters.getPosition();
            if (!isKeyPosition(position)) {
                return;
            }
            Optional<ConfigSchemaLookup> lookup = ConfigSchemaLookup.create(position);
            if (lookup.isEmpty()) {
                return;
            }
            ConfigSchemaLookup schemaLookup = lookup.get();
            Optional<ObjectNode> objectSchema = schemaLookup.enclosingObjectSchema(position);
            if (objectSchema.isEmpty()) {
                return;
            }
            Map<String, ObjectNode> properties = schemaLookup.properties(objectSchema.get());
            if (properties.isEmpty()) {
                return;
            }
            Set<String> declared = declaredProperties(position);
            Set<String> required = requiredProperties(objectSchema.get());

            properties.forEach((name, propertySchema) -> {
                if (declared.contains(name)) {
                    return;
                }
                ObjectNode resolved = schemaLookup.deref(propertySchema);
                if (resolved == null) {
                    return;
                }
                LookupElementBuilder element = LookupElementBuilder.create(name)
                        .withIcon(AllIcons.Nodes.Property)
                        .withBoldness(required.contains(name))
                        .withTypeText(schemaLookup.typeText(resolved))
                        .withInsertHandler(
                                new ConfigPropertyInsertHandler(schemaLookup, resolved));
                result.addElement(element);
            });
            result.stopHere();
        }

        private static boolean isKeyPosition(@NotNull PsiElement position) {
            if (PsiTreeUtil.getParentOfType(position, JSObjectLiteralExpression.class, false)
                    == null) {
                return false;
            }
            JSProperty property =
                    PsiTreeUtil.getParentOfType(position, JSProperty.class, false);
            return property == null || !ConfigSchemaLookup.isValuePosition(property, position);
        }

        private static Set<String> declaredProperties(@NotNull PsiElement position) {
            JSObjectLiteralExpression object =
                    PsiTreeUtil.getParentOfType(position, JSObjectLiteralExpression.class, false);
            if (object == null) {
                return Set.of();
            }
            Set<String> names = new HashSet<>();
            for (JSProperty property : object.getProperties()) {
                String name = property.getName();
                if (name != null && !PsiTreeUtil.isAncestor(property, position, false)) {
                    names.add(name);
                }
            }
            return names;
        }

        private static Set<String> requiredProperties(@NotNull ObjectNode objectSchema) {
            if (!objectSchema.path("required").isArray()) {
                return Set.of();
            }
            Set<String> names = new HashSet<>();
            objectSchema.path("required").forEach(name -> names.add(name.asText()));
            return names;
        }
    }
}
