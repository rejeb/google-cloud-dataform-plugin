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

import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.compilation.model.CompiledTable;
import io.github.rejeb.dataform.language.compilation.model.Declaration;
import io.github.rejeb.dataform.language.compilation.model.CompiledOperation;
import io.github.rejeb.dataform.language.compilation.model.Target;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public final class TestFileNames {

    public static final String TESTS_DIR = "tests";

    private TestFileNames() {
    }

    /**
     * Returns the target a test names in its {@code dataset}: the canonical target, which is what
     * the project writes in {@code ref()} before any table prefix, or the target itself.
     */
    @NotNull
    public static Target testedTarget(@NotNull CompiledTable table) {
        return table.getCanonicalTarget() != null ? table.getCanonicalTarget() : table.getTarget();
    }

    /**
     * Tells whether another action of the graph compiles to a target of the same name, so that the
     * bare name would not designate the given one alone.
     */
    public static boolean isAmbiguous(@NotNull CompiledGraph graph, @NotNull Target target) {
        return Stream.of(
                        present(graph.getTables()).stream().map(CompiledTable::getTarget),
                        present(graph.getDeclarations()).stream().map(Declaration::getTarget),
                        present(graph.getOperations()).stream().map(CompiledOperation::getTarget))
                .flatMap(targets -> targets)
                .filter(Objects::nonNull)
                .filter(other -> Objects.equals(target.getName(), other.getName()))
                .map(Target::getFullName)
                .distinct()
                .count() > 1;
    }

    /**
     * Returns the name of the test file of a target, qualified by its schema when the name is
     * ambiguous.
     */
    @NotNull
    public static String fileName(@NotNull Target tested, boolean ambiguous) {
        return "test_" + (ambiguous ? tested.getSchema() + "_" : "") + tested.getName() + ".sqlx";
    }

    private static <T> List<T> present(List<T> list) {
        return list == null ? List.of() : list;
    }
}
