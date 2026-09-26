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
package io.github.rejeb.dataform.language.diagnostics.sql.hint;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Map;

record FakeScope(@NotNull Map<String, List<String>> columnsBySource,
                 @NotNull Collection<String> functions,
                 @NotNull Collection<String> actionNames,
                 int groupByEnd,
                 @NotNull List<SelectItem> items) implements SqlScope {

    static FakeScope columns(Map<String, List<String>> columnsBySource) {
        return new FakeScope(columnsBySource, List.of(), List.of(), -1, List.of());
    }

    @Override
    public @NotNull List<SelectItem> selectItemsNamed(@NotNull String name) {
        return items;
    }
}
