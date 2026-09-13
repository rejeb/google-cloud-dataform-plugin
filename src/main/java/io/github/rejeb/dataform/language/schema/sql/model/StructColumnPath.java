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
package io.github.rejeb.dataform.language.schema.sql.model;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * A column of a Dataform table, or one field reached inside it.
 *
 * <p>A struct column holds fields, and those fields hold fields of their own. What a reader points
 * at is therefore not a column but a path into one: {@code customer}, {@code customer.origin} and
 * {@code customer.origin.code} are three different things to navigate to, and only the first is a
 * column the schema lists.</p>
 *
 * @param root  the column the path starts at, which the schema of its table holds
 * @param trail the fields walked into it, empty when the path is the column itself
 */
public record StructColumnPath(@NotNull DataformDasColumn root, @NotNull List<ColumnInfo> trail) {

    public StructColumnPath(@NotNull DataformDasColumn root, @NotNull List<ColumnInfo> trail) {
        this.root = root;
        this.trail = List.copyOf(trail);
    }

    /** Whether the path ends on a field of the column rather than on the column itself. */
    public boolean isField() {
        return !trail.isEmpty();
    }

    /** What the path ends on, which is the column itself when it walks into no field. */
    public @NotNull ColumnInfo leaf() {
        return trail.isEmpty() ? root.getColumnInfo() : trail.getLast();
    }

    /** The name the window shows and the search looks for: the last segment, on its own. */
    public @NotNull String leafName() {
        return leaf().name();
    }

    /** The segments from the column down to the leaf. */
    public @NotNull List<String> segments() {
        List<String> names = new ArrayList<>(trail.size() + 1);
        names.add(root.getName());
        for (ColumnInfo field : trail) names.add(field.name());
        return names;
    }

    /** The path as a reader writes it, {@code customer.origin.code}. */
    public @NotNull String dottedName() {
        return String.join(".", segments());
    }

    /**
     * Whether two paths name the same field of the same column.
     *
     * <p>A column is rebuilt on every resolve, so it is compared on the logical identity its table
     * gives it rather than by instance. Segments are compared the way the rest of this plugin
     * compares column names, which is without regard to case.</p>
     */
    public boolean sameAs(@NotNull StructColumnPath other) {
        if (!root.isEquivalentTo(other.root)) return false;
        List<String> mine = segments();
        List<String> theirs = other.segments();
        if (mine.size() != theirs.size()) return false;
        for (int i = 0; i < mine.size(); i++) {
            if (!mine.get(i).equalsIgnoreCase(theirs.get(i))) return false;
        }
        return true;
    }
}
