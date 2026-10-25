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
package io.github.rejeb.dataform.language.columns.usages;

import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * One line of the column window: either a group heading, or a place the column is declared or read.
 *
 * <p>An entry keeps the expression split around the column name so the name can be drawn apart from
 * the rest, and names the file and line it sits in rather than the project it belongs to.</p>
 *
 * <p>Where a row opens is a place in a host file, never the injected element the search found. A row
 * is opened from a mouse listener, and opening an injected file has the editor validate the
 * injection there — on the event thread, outside a read action. Holding the host place also makes a
 * row open the very line it names.</p>
 *
 * @param group    the heading this row sits under, which is the heading's own name for a heading
 * @param truncated whether the group holds the reads the search stopped at rather than all of them
 * @param location the file and line this row points at, as {@code file.sqlx:23}
 * @param target   the place to open in the host file, or {@code null} for a heading and a BigQuery column
 */
public record ColumnUsageRow(@NotNull Kind kind, @NotNull String group, @NotNull String heading, int count,
                             @NotNull String before, @NotNull String name, @NotNull String after,
                             @NotNull String location, @Nullable OpenFileDescriptor target, boolean truncated) {

    /**
     * What a row stands for. The heading of a group, or a place within it: where the column is built
     * from, a field it holds, or a place reading it.
     */
    public enum Kind {
        HEADING,
        DECLARATION,
        FIELD,
        USAGE
    }

    /** What the location of a BigQuery column row starts with, before the table's full name. */
    public static final String BIGQUERY_TABLE = "BigQuery table ";

    /**
     * A group heading. A truncated heading carries the reads the search stopped at rather than the
     * reads the project holds, and says so, because a count read as a total is worse than no count.
     */
    static @NotNull ColumnUsageRow heading(@NotNull String heading, int count, boolean truncated) {
        return new ColumnUsageRow(Kind.HEADING, heading, heading, count, "", "", "", "", null,
                truncated);
    }

    static @NotNull ColumnUsageRow entry(@NotNull Kind kind, @NotNull String group,
                                         @NotNull String before, @NotNull String name,
                                         @NotNull String after, @NotNull String location,
                                         @NotNull OpenFileDescriptor target) {
        return new ColumnUsageRow(kind, group, "", 0, before, name, after, location, target,
                false);
    }

    /**
     * A column of a BigQuery table the project reads but does not build. No line of the project
     * declares it, so the row names the table and opens nothing.
     */
    static @NotNull ColumnUsageRow bigQueryColumn(@NotNull String group,
                                                  @NotNull String columnName,
                                                  @NotNull String tableFullName) {
        return new ColumnUsageRow(Kind.DECLARATION, group, "", 0, "", columnName, "",
                BIGQUERY_TABLE + tableFullName, null, false);
    }

    public boolean isHeading() {
        return kind == Kind.HEADING;
    }

    @Override
    public String toString() {
        return isHeading()
                ? heading + " (" + count + (truncated ? "+" : "") + ")"
                : before + name + after + "  " + location;
    }
}
