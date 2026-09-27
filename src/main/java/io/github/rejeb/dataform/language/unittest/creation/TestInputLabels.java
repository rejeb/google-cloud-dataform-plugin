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
package io.github.rejeb.dataform.language.unittest.creation;

import io.github.rejeb.dataform.language.compilation.model.ActionReference;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.compilation.model.Target;
import io.github.rejeb.dataform.language.injection.SqlxRefCall;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Labels the inputs of a generated unit test the way Dataform core matches them: exactly as the
 * tested query wrote the {@code ref()} of each dependency, before any table prefix or schema suffix.
 */
public final class TestInputLabels {

    private static final Pattern REF_CALL = Pattern.compile("(?<![\\w$])ref\\s*\\(([^()]*)\\)");

    private final Map<String, List<String>> written;

    private TestInputLabels(@NotNull Map<String, List<String>> written) {
        this.written = written;
    }

    /**
     * Reads the {@code ref()} calls of the tested action's source and keeps, for each target they
     * resolve to, the parts the first call wrote.
     *
     * @param graph  the compiled graph resolving the calls
     * @param source the text of the file declaring the tested action, {@code null} when unknown
     * @return the labels
     */
    public static @NotNull TestInputLabels of(@NotNull CompiledGraph graph, @Nullable String source) {
        Map<String, List<String>> written = new HashMap<>();
        if (source != null) {
            Matcher call = REF_CALL.matcher(source);
            while (call.find()) {
                SqlxRefCall.parseArguments(call.group(1)).ifPresent(reference ->
                        graph.findTargetByReference(reference)
                                .filter(target -> target.getFullName() != null)
                                .ifPresent(target -> written.putIfAbsent(target.getFullName(), parts(reference))));
            }
        }
        return new TestInputLabels(written);
    }

    /**
     * Returns the label parts of an input: those its {@code ref()} wrote, else its name, qualified
     * by its schema when the name alone is ambiguous.
     *
     * @param target    the dependency the input stands for
     * @param ambiguous whether the name alone designates several actions
     * @return the label parts, one to three
     */
    public @NotNull List<String> partsOf(@NotNull Target target, boolean ambiguous) {
        List<String> parts = target.getFullName() == null ? null : written.get(target.getFullName());
        if (parts != null) {
            return parts;
        }
        return ambiguous ? List.of(target.getSchema(), target.getName()) : List.of(target.getName());
    }

    private static List<String> parts(@NotNull ActionReference reference) {
        List<String> parts = new ArrayList<>(3);
        if (reference.database() != null) {
            parts.add(reference.database());
        }
        if (reference.schema() != null) {
            parts.add(reference.schema());
        }
        parts.add(reference.name());
        return List.copyOf(parts);
    }
}
