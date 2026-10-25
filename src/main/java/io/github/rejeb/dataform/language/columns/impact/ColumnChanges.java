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

import io.github.rejeb.dataform.language.schema.sql.model.ColumnInfo;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Compares the columns of the actions before and after a schema refresh. Added columns break no
 * reader and are not reported. A column gone while one of the same type took its place in the
 * column list, or the only one added, is taken as renamed.
 */
final class ColumnChanges {

    private ColumnChanges() {
    }

    static @NotNull List<ColumnChange> between(@NotNull Map<String, List<ColumnInfo>> before,
                                               @NotNull Map<String, List<ColumnInfo>> after) {
        List<ColumnChange> changes = new ArrayList<>();
        new TreeMap<>(before).forEach((table, oldColumns) -> {
            List<ColumnInfo> newColumns = after.get(table);
            if (newColumns != null) changes.addAll(ofTable(table, oldColumns, newColumns));
        });
        return changes;
    }

    private static @NotNull List<ColumnChange> ofTable(@NotNull String table,
                                                       @NotNull List<ColumnInfo> oldColumns,
                                                       @NotNull List<ColumnInfo> newColumns) {
        Map<String, ColumnInfo> newByName = byName(newColumns);
        Set<String> oldNames = byName(oldColumns).keySet();
        List<ColumnInfo> added = newColumns.stream().filter(c -> !oldNames.contains(key(c))).toList();
        List<ColumnInfo> removed = oldColumns.stream().filter(c -> !newByName.containsKey(key(c))).toList();
        Set<ColumnInfo> takenAsRenamed = new HashSet<>();
        List<ColumnChange> changes = new ArrayList<>();
        for (ColumnInfo column : oldColumns) {
            ColumnInfo now = newByName.get(key(column));
            if (now != null) {
                if (!typeOf(now).equals(typeOf(column))) {
                    changes.add(new ColumnChange(table, ColumnChange.Kind.TYPE_CHANGED, column.name(), now.name(),
                            typeOf(column), typeOf(now)));
                }
                continue;
            }
            ColumnInfo renamed = renamedTo(column, oldColumns.indexOf(column), newColumns, added, removed, takenAsRenamed);
            if (renamed != null) {
                takenAsRenamed.add(renamed);
                changes.add(new ColumnChange(table, ColumnChange.Kind.RENAMED, column.name(), renamed.name(),
                        typeOf(column), typeOf(renamed)));
            } else {
                changes.add(new ColumnChange(table, ColumnChange.Kind.REMOVED, column.name(), null,
                        typeOf(column), null));
            }
        }
        return changes;
    }

    private static ColumnInfo renamedTo(@NotNull ColumnInfo column,
                                        int index,
                                        @NotNull List<ColumnInfo> newColumns,
                                        @NotNull List<ColumnInfo> added,
                                        @NotNull List<ColumnInfo> removed,
                                        @NotNull Set<ColumnInfo> taken) {
        if (index < newColumns.size()) {
            ColumnInfo inPlace = newColumns.get(index);
            if (added.contains(inPlace) && !taken.contains(inPlace) && typeOf(inPlace).equals(typeOf(column))) {
                return inPlace;
            }
        }
        if (added.size() == 1 && removed.size() == 1 && !taken.contains(added.getFirst())
                && typeOf(added.getFirst()).equals(typeOf(column))) {
            return added.getFirst();
        }
        return null;
    }

    private static @NotNull Map<String, ColumnInfo> byName(@NotNull List<ColumnInfo> columns) {
        Map<String, ColumnInfo> byName = new TreeMap<>();
        columns.forEach(c -> byName.putIfAbsent(key(c), c));
        return byName;
    }

    private static @NotNull String key(@NotNull ColumnInfo column) {
        return column.name().toLowerCase(Locale.ROOT);
    }

    private static @NotNull String typeOf(@NotNull ColumnInfo column) {
        return "REPEATED".equals(column.mode()) ? "ARRAY<" + column.type() + ">" : column.type();
    }
}
