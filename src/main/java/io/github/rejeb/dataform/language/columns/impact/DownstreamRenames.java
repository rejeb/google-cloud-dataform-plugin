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
package io.github.rejeb.dataform.language.columns.impact;

import io.github.rejeb.dataform.language.columns.rename.downstream.DownstreamRename;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Turns the column renames found by an impact check into downstream renames. A rename an action
 * passes on with a star shows up as a rename of that action too; it belongs to the action it was
 * made in, the one of the group reading none of the others.
 */
final class DownstreamRenames {

    private DownstreamRenames() {
    }

    /**
     * @param changes the column changes found by the check
     * @param readsOf the full names of the actions each action reads directly
     * @return one downstream rename per action a column was renamed in
     */
    static @NotNull List<DownstreamRename> of(@NotNull List<ColumnChange> changes,
                                              @NotNull Map<String, Set<String>> readsOf) {
        Map<String, List<ColumnChange>> groups = new LinkedHashMap<>();
        for (ColumnChange change : changes) {
            if (change.kind() != ColumnChange.Kind.RENAMED || change.newName() == null) continue;
            String key = change.oldName().toLowerCase(Locale.ROOT) + "\n" + change.newName().toLowerCase(Locale.ROOT);
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(change);
        }
        List<DownstreamRename> renames = new ArrayList<>();
        for (List<ColumnChange> group : groups.values()) {
            Set<String> tables = new LinkedHashSet<>();
            group.forEach(change -> tables.add(change.tableFullName()));
            for (ColumnChange change : group) {
                boolean passedOn = readsOf.getOrDefault(change.tableFullName(), Set.of()).stream().anyMatch(tables::contains);
                if (!passedOn) {
                    renames.add(new DownstreamRename(change.tableFullName(), change.oldName(), change.newName(),
                            Set.copyOf(tables)));
                }
            }
        }
        return renames;
    }
}
