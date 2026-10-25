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
package io.github.rejeb.dataform.language.schema.sql;

import io.github.rejeb.dataform.language.compilation.model.SortableAction;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

/**
 * The actions a schema refresh dry-runs, grouped into waves, and those among them that changed
 * themselves. The others are planned only because they read a changed action: they need a dry-run
 * only when one of the schemas they read turns out to be different.
 *
 * @param waves    the actions to dry-run, every wave depending only on earlier ones
 * @param modified the full names of the actions to dry-run whatever their dependencies return
 */
record SchemaRefreshPlan(@NotNull List<List<SortableAction>> waves, @NotNull Set<String> modified) {

    static final SchemaRefreshPlan EMPTY = new SchemaRefreshPlan(List.of(), Set.of());

    /**
     * Whether the action must be dry-run whatever its dependencies return.
     */
    boolean isModified(@NotNull SortableAction action) {
        return modified.contains(action.target().getFullName());
    }
}
