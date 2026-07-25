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
package io.github.rejeb.dataform.language.lineage.column;

import io.github.rejeb.dataform.language.lineage.graph.LineageNode;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Objects;

public record ColumnRef(@NotNull String tableFullName, @NotNull String columnName) {

    public @NotNull String id() {
        return tableFullName + "#" + columnName;
    }

    public @NotNull String tableNodeId() {
        return LineageNode.idOf(tableFullName);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ColumnRef other)) return false;
        return tableFullName.equals(other.tableFullName)
                && columnName.equalsIgnoreCase(other.columnName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tableFullName, columnName.toLowerCase(Locale.ROOT));
    }
}
