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
package io.github.rejeb.dataform.language.validation;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.javascript.psi.JSLiteralExpression;
import com.intellij.lang.javascript.psi.JSProperty;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import io.github.rejeb.dataform.language.completion.config.ConfigSchemaLookup;
import io.github.rejeb.dataform.language.psi.SqlxConfigBlock;
import io.github.rejeb.dataform.language.psi.SqlxFile;
import io.github.rejeb.dataform.language.schema.json.DataformJsonSchemaGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reports unknown keys and invalid enumerated values inside the SQLX config block.
 */
public final class ConfigBlockValidator implements SqlxValidator {

    @Override
    public @NotNull List<SqlxValidationProblem> validate(@NotNull PsiFile file) {
        if (!(file instanceof SqlxFile)) {
            return List.of();
        }
        SqlxConfigBlock configBlock = PsiTreeUtil.findChildOfType(file, SqlxConfigBlock.class);
        if (configBlock == null) {
            return List.of();
        }
        Optional<ObjectNode> schema = file.getProject()
                .getService(DataformJsonSchemaGenerator.class)
                .generateSqlxConfigSchema();
        if (schema.isEmpty()) {
            return List.of();
        }

        ConfigSchemaLookup lookup = ConfigSchemaLookup.of(schema.get());
        InjectedLanguageManager manager = InjectedLanguageManager.getInstance(file.getProject());
        List<SqlxValidationProblem> problems = new ArrayList<>();

        manager.enumerate(configBlock, (injectedPsi, places) ->
                collect(injectedPsi, manager, lookup, problems));
        return List.copyOf(problems);
    }

    private static void collect(@NotNull PsiFile injectedPsi,
                                @NotNull InjectedLanguageManager manager,
                                @NotNull ConfigSchemaLookup lookup,
                                @NotNull List<SqlxValidationProblem> problems) {
        for (JSProperty property :
                PsiTreeUtil.findChildrenOfType(injectedPsi, JSProperty.class)) {
            String name = property.getName();
            PsiElement nameIdentifier = property.getNameIdentifier();
            if (name == null || nameIdentifier == null) {
                continue;
            }
            Optional<ObjectNode> objectSchema = lookup.enclosingObjectSchema(property);
            if (objectSchema.isEmpty()) {
                continue;
            }
            Map<String, ObjectNode> declared = lookup.properties(objectSchema.get());
            if (declared.isEmpty()) {
                continue;
            }
            if (!declared.containsKey(name)) {
                problems.add(new SqlxValidationProblem(
                        manager.injectedToHost(injectedPsi, nameIdentifier.getTextRange()),
                        "Unknown Dataform config property \"" + name + "\"",
                        SqlxValidationProblem.Kind.UNKNOWN_CONFIG_KEY));
                continue;
            }
            checkEnumValue(injectedPsi, manager, lookup, declared.get(name), property, problems);
        }
    }

    private static void checkEnumValue(@NotNull PsiFile injectedPsi,
                                       @NotNull InjectedLanguageManager manager,
                                       @NotNull ConfigSchemaLookup lookup,
                                       @NotNull ObjectNode propertySchema,
                                       @NotNull JSProperty property,
                                       @NotNull List<SqlxValidationProblem> problems) {
        ObjectNode resolved = lookup.deref(propertySchema);
        if (resolved == null) {
            return;
        }
        List<String> allowed = lookup.enumValues(resolved);
        if (allowed.isEmpty()) {
            return;
        }
        String value = literalValue(property.getValue());
        if (value == null || allowed.contains(value)) {
            return;
        }
        problems.add(new SqlxValidationProblem(
                manager.injectedToHost(injectedPsi, property.getValue().getTextRange()),
                "Invalid value \"" + value + "\", expected one of " + String.join(", ", allowed),
                SqlxValidationProblem.Kind.INVALID_CONFIG_VALUE));
    }

    private static @Nullable String literalValue(@Nullable PsiElement value) {
        if (!(value instanceof JSLiteralExpression literal) || !literal.isQuotedLiteral()) {
            return null;
        }
        return literal.getStringValue();
    }
}
