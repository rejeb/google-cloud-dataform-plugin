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

import io.github.rejeb.dataform.language.compilation.model.Target;
import io.github.rejeb.dataform.language.schema.sql.model.ColumnInfo;
import io.github.rejeb.dataform.language.unittest.generation.TestSelectGenerator;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

public final class TestFileContent {

    private static final String INPUT_INDENT = "  ";
    private static final String PLACEHOLDER = "SELECT\n%s  1 AS id";

    private TestFileContent() {
    }

    /**
     * Returns the text of a unit test of {@code tested}: its config, naming the tested action by its
     * schema and name, one input per distinct dependency and the expected output, each filled from
     * the columns known for its target. A target without known columns gets a placeholder row and a
     * reminder comment.
     */
    @NotNull
    public static String of(@NotNull Target tested,
                            @NotNull List<Target> inputs,
                            @NotNull Predicate<Target> ambiguous,
                            @NotNull Function<Target, List<ColumnInfo>> columns) {
        StringBuilder text = new StringBuilder()
                .append("config {\n  type: \"test\",\n  dataset: {\n    schema: \"")
                .append(tested.getSchema())
                .append("\",\n    name: \"")
                .append(tested.getName())
                .append("\"\n  }\n}\n\n");
        for (Target input : distinct(inputs)) {
            List<ColumnInfo> inputColumns = columns.apply(input);
            if (inputColumns.isEmpty()) {
                text.append(reminder(input));
            }
            text.append("input ").append(label(input, ambiguous.test(input))).append(" {\n")
                    .append(INPUT_INDENT).append(select(inputColumns, INPUT_INDENT)).append("\n}\n\n");
        }
        List<ColumnInfo> expected = columns.apply(tested);
        if (expected.isEmpty()) {
            text.append(reminder(tested));
        }
        return text.append("-- Expected output\n").append(select(expected, "")).append("\n").toString();
    }

    private static String select(@NotNull List<ColumnInfo> columns, @NotNull String indent) {
        return columns.isEmpty() ? PLACEHOLDER.formatted(indent) : TestSelectGenerator.select(columns, indent);
    }

    private static String reminder(@NotNull Target target) {
        return "-- TODO: schema of " + target.getName() + " not extracted yet\n";
    }

    private static String label(@NotNull Target target, boolean ambiguous) {
        return ambiguous
                ? "\"" + target.getSchema() + "\", \"" + target.getName() + "\""
                : "\"" + target.getName() + "\"";
    }

    private static List<Target> distinct(@NotNull List<Target> targets) {
        Map<String, Target> unique = new LinkedHashMap<>();
        for (Target target : targets) {
            if (target != null && target.getName() != null) {
                unique.putIfAbsent(target.getFullName(), target);
            }
        }
        return List.copyOf(unique.values());
    }
}
