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
package io.github.rejeb.dataform.language.unittest;

import com.intellij.openapi.vfs.VirtualFile;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.compilation.model.CompiledTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

public final class TestableActions {

    private static final Set<String> TESTABLE_TYPES = Set.of("table", "view");

    private TestableActions() {
    }

    /**
     * Tells whether Dataform accepts a unit test on the given action. Only tables and views are
     * accepted; incremental tables, operations, assertions and declarations are rejected by the
     * compiler.
     */
    public static boolean isTestable(@Nullable CompiledTable table) {
        return table != null && table.getTarget() != null && TESTABLE_TYPES.contains(table.getActionKind());
    }

    /**
     * Returns the testable actions compiled from the given file, in graph order.
     */
    @NotNull
    public static List<CompiledTable> in(@NotNull CompiledGraph graph, @NotNull VirtualFile file) {
        return graph.findTableByFileName(file.getPath()).stream()
                .filter(TestableActions::isTestable)
                .toList();
    }
}
