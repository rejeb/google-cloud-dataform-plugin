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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A change of a column of an action that the actions reading it may not survive.
 *
 * @param tableFullName the full name of the action whose column changed
 * @param kind          what happened to the column
 * @param oldName       the name the column had
 * @param newName       the name it has now, {@code null} when it is gone
 * @param oldType       the type the column had
 * @param newType       the type it has now, {@code null} when it is gone
 */
public record ColumnChange(@NotNull String tableFullName,
                           @NotNull Kind kind,
                           @NotNull String oldName,
                           @Nullable String newName,
                           @NotNull String oldType,
                           @Nullable String newType) {

    public enum Kind {
        RENAMED,
        REMOVED,
        TYPE_CHANGED
    }

    /**
     * The change in words, naming the table by its short name.
     */
    public @NotNull String describe() {
        String table = "`" + tableFullName.substring(tableFullName.lastIndexOf('.') + 1) + "`";
        return switch (kind) {
            case RENAMED -> "Column `" + oldName + "` was renamed to `" + newName + "` in " + table;
            case REMOVED -> "Column `" + oldName + "` no longer exists in " + table;
            case TYPE_CHANGED -> "Column `" + oldName + "` changed type from " + oldType + " to " + newType + " in " + table;
        };
    }
}
