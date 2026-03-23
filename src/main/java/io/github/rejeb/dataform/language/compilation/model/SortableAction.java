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
package io.github.rejeb.dataform.language.compilation.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public record SortableAction(
        @NotNull Target target,
        @NotNull List<Target> dependencyTargets,
        @Nullable CompiledTable table,
        @Nullable CompiledOperation operation,
        @Nullable Declaration declaration
) {
    public static SortableAction of(@NotNull CompiledTable table) {
        return new SortableAction(table.getTarget(), table.getDependencyTargets(), table, null, null);
    }

    public static SortableAction of(@NotNull CompiledOperation operation) {
        return new SortableAction(operation.getTarget(), operation.getDependencyTargets(), null, operation, null);
    }

    public static SortableAction of(@NotNull Declaration declaration) {
        return new SortableAction(declaration.getTarget(), List.of(), null, null, declaration);
    }

    public boolean isTable() { return table != null; }
    public boolean isOperation() { return operation != null; }
    public boolean isDeclaration() { return declaration != null; }
}
