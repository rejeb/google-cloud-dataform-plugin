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
package io.github.rejeb.dataform.language.reference;

import com.intellij.lang.javascript.psi.JSArgumentList;
import com.intellij.lang.javascript.psi.JSArrayLiteralExpression;
import com.intellij.lang.javascript.psi.JSCallExpression;
import com.intellij.lang.javascript.psi.JSExpression;
import com.intellij.lang.javascript.psi.JSLiteralExpression;
import com.intellij.lang.javascript.psi.JSObjectLiteralExpression;
import com.intellij.lang.javascript.psi.JSProperty;
import com.intellij.lang.javascript.psi.JSReferenceExpression;
import io.github.rejeb.dataform.language.compilation.model.ActionReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

/**
 * Reads the action a string literal names when it is the name argument of a {@code ref()} or
 * {@code resolve()} call, in every form Dataform accepts: {@code ref("name")},
 * {@code ref("schema", "name")}, {@code ref("database", "schema", "name")}, the same one to three
 * parts as a single array, and {@code ref({database, schema, name})} or
 * {@code ref({project, dataset, name})}. The schema and database parts name no action.
 */
public final class RefCallLiterals {

    private static final Set<String> REF_FUNCTIONS = Set.of("ref", "resolve");
    private static final int MAX_POSITIONAL_ARGUMENTS = 3;

    private RefCallLiterals() {
    }

    /**
     * The action designated by the call the literal is the name argument of.
     *
     * @param literal the string literal
     * @return the action, qualified by the schema and database the call gives as literals, empty when
     * the literal is not the name argument of a {@code ref()} or {@code resolve()} call
     */
    public static @NotNull Optional<ActionReference> designatedBy(@NotNull JSLiteralExpression literal) {
        String name = stringValue(literal);
        if (name == null) {
            return Optional.empty();
        }
        if (literal.getParent() instanceof JSArgumentList arguments) {
            return isRefCall(arguments) ? parts(arguments.getArguments(), literal, name) : Optional.empty();
        }
        if (literal.getParent() instanceof JSArrayLiteralExpression array
                && isSoleRefArgument(array)) {
            return parts(array.getExpressions(), literal, name);
        }
        if (literal.getParent() instanceof JSProperty property
                && "name".equals(property.getName())
                && property.getValue() == literal
                && property.getParent() instanceof JSObjectLiteralExpression object
                && isSoleRefArgument(object)) {
            return Optional.of(object.findProperty("project") != null || object.findProperty("dataset") != null
                    ? new ActionReference(propertyValue(object, "project"), propertyValue(object, "dataset"), name)
                    : new ActionReference(propertyValue(object, "database"), propertyValue(object, "schema"), name));
        }
        return Optional.empty();
    }

    private static boolean isSoleRefArgument(@NotNull JSExpression argument) {
        return argument.getParent() instanceof JSArgumentList arguments
                && arguments.getArguments().length == 1
                && isRefCall(arguments);
    }

    private static @NotNull Optional<ActionReference> parts(@NotNull JSExpression[] values,
                                                            @NotNull JSLiteralExpression literal,
                                                            @NotNull String name) {
        if (values.length > MAX_POSITIONAL_ARGUMENTS
                || values[values.length - 1] != literal
                || Arrays.stream(values).anyMatch(value -> value instanceof JSArrayLiteralExpression
                        || value instanceof JSObjectLiteralExpression)) {
            return Optional.empty();
        }
        return Optional.of(switch (values.length) {
            case 1 -> ActionReference.named(name);
            case 2 -> new ActionReference(null, stringValue(values[0]), name);
            default -> new ActionReference(stringValue(values[0]), stringValue(values[1]), name);
        });
    }

    private static boolean isRefCall(@NotNull JSArgumentList arguments) {
        return arguments.getParent() instanceof JSCallExpression call && isRefCall(call);
    }

    /**
     * Tells whether a call is a {@code ref()} or a {@code resolve()} call.
     */
    public static boolean isRefCall(@NotNull JSCallExpression call) {
        return call.getMethodExpression() instanceof JSReferenceExpression method
                && REF_FUNCTIONS.contains(method.getReferenceName());
    }

    private static @Nullable String propertyValue(@NotNull JSObjectLiteralExpression object,
                                                  @NotNull String key) {
        JSProperty property = object.findProperty(key);
        return property == null ? null : stringValue(property.getValue());
    }

    /**
     * The text of a quoted string literal, or {@code null} for any other expression.
     */
    public static @Nullable String stringValue(@Nullable JSExpression expression) {
        return expression instanceof JSLiteralExpression literal
                && literal.isQuotedLiteral()
                && literal.getValue() instanceof String value
                ? value
                : null;
    }
}
