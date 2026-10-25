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
package io.github.rejeb.dataform.language.columns.rename.downstream;

import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * A column renamed in its action while the actions downstream still read it under its old name.
 *
 * @param tableFullName the full name of the action the column was renamed in
 * @param oldName       the name the downstream actions still read
 * @param newName       the name the column has now
 * @param renamedTables the full names of the actions publishing the column under the new name since
 *                      the change: the action itself and those passing its columns on with a star
 */
public record DownstreamRename(@NotNull String tableFullName,
                               @NotNull String oldName,
                               @NotNull String newName,
                               @NotNull Set<String> renamedTables) {

    /**
     * The rename in words, naming the table by its short name.
     */
    public @NotNull String describe() {
        return "Column `" + oldName + "` was renamed to `" + newName + "` in `"
                + tableFullName.substring(tableFullName.lastIndexOf('.') + 1) + "`";
    }
}
