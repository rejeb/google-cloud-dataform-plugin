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
package io.github.rejeb.dataform.language.injection;

import io.github.rejeb.dataform.language.compilation.model.ActionReference;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the action a {@code ${ref(...)}} or {@code ${resolve(...)}} template designates, in every
 * form Dataform accepts: {@code ref("name")}, {@code ref("schema", "name")},
 * {@code ref("database", "schema", "name")}, the same one to three parts as a single array, and
 * {@code ref({database, schema, name})} or {@code ref({project, dataset, name})}.
 */
public final class SqlxRefCall {

    private static final Pattern REF_CALL = Pattern.compile(
            "\\$\\{\\s*(?:ref|resolve)\\s*\\((.*)\\)\\s*}", Pattern.DOTALL);

    private static final Pattern STRING_ARGUMENTS = Pattern.compile(
            "\\s*(['\"])([^'\"]+)\\1\\s*(?:,\\s*(['\"])([^'\"]+)\\3\\s*)?(?:,\\s*(['\"])([^'\"]+)\\5\\s*)?");

    private static final Pattern ARRAY_ARGUMENT = Pattern.compile("\\s*\\[(.*)]\\s*", Pattern.DOTALL);

    private static final Pattern OBJECT_ARGUMENT = Pattern.compile("\\s*\\{(.*)}\\s*", Pattern.DOTALL);

    private static final Pattern OBJECT_PROPERTY = Pattern.compile(
            "(['\"]?)(database|schema|project|dataset|name)\\1\\s*:\\s*(['\"])([^'\"]*)\\3");

    private SqlxRefCall() {
    }

    /**
     * The action a template designates.
     *
     * @param holeText the template, {@code ${...}} included
     * @return the reference, empty when the template is no {@code ref()} call of literal arguments
     */
    public static @NotNull Optional<ActionReference> parse(@NotNull String holeText) {
        Matcher call = REF_CALL.matcher(holeText.trim());
        if (!call.matches()) return Optional.empty();
        return parseArguments(call.group(1));
    }

    /**
     * The action the arguments of a {@code ref()} call designate.
     *
     * @param arguments the text between the parentheses of the call
     * @return the reference, empty when the arguments are not literal
     */
    public static @NotNull Optional<ActionReference> parseArguments(@NotNull String arguments) {
        Matcher strings = STRING_ARGUMENTS.matcher(arguments);
        if (strings.matches()) return Optional.of(fromStrings(strings));

        Matcher array = ARRAY_ARGUMENT.matcher(arguments);
        if (array.matches()) {
            Matcher parts = STRING_ARGUMENTS.matcher(array.group(1));
            return parts.matches() ? Optional.of(fromStrings(parts)) : Optional.empty();
        }

        Matcher object = OBJECT_ARGUMENT.matcher(arguments);
        if (object.matches()) return fromObject(object.group(1));

        return Optional.empty();
    }

    private static @NotNull ActionReference fromStrings(@NotNull Matcher strings) {
        List<String> parts = new ArrayList<>(3);
        for (int group = 2; group <= 6; group += 2) {
            if (strings.group(group) != null) parts.add(strings.group(group));
        }
        return switch (parts.size()) {
            case 1 -> ActionReference.named(parts.get(0));
            case 2 -> new ActionReference(null, parts.get(0), parts.get(1));
            default -> new ActionReference(parts.get(0), parts.get(1), parts.get(2));
        };
    }

    private static @NotNull Optional<ActionReference> fromObject(@NotNull String body) {
        Map<String, String> properties = new HashMap<>();
        Matcher property = OBJECT_PROPERTY.matcher(body);
        while (property.find()) {
            properties.put(property.group(2), property.group(4));
        }
        String name = properties.get("name");
        if (name == null || name.isBlank()) return Optional.empty();
        boolean configTarget = properties.containsKey("project") || properties.containsKey("dataset");
        return Optional.of(configTarget
                ? new ActionReference(properties.get("project"), properties.get("dataset"), name)
                : new ActionReference(properties.get("database"), properties.get("schema"), name));
    }
}
