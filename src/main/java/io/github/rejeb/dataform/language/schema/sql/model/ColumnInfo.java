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

import com.intellij.database.model.properties.PropertyConverter;
import com.intellij.database.types.DasType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public record ColumnInfo(
        @NotNull String name,
        @NotNull String type,
        @NotNull String mode,
        @Nullable String description,
        @NotNull List<ColumnInfo> subFields
) implements Serializable {

    private static final Map<String, DasType> TYPES = new ConcurrentHashMap<>();
    public ColumnInfo(@NotNull String name, @NotNull String type, @NotNull String mode, @Nullable String description) {
        this(name, type, mode, description, Collections.emptyList());
    }

    /**
     * The database type of this column. Importing a type builds throwaway PSI for it, so the
     * result is memoised per type specification: a resolve that reaches a struct field must see
     * the same element every time, otherwise the platform resolve cache is non-idempotent.
     */
    public DasType dasType() {
        return TYPES.computeIfAbsent(typeSpecification(), PropertyConverter::importDasType);
    }

    /**
     * The type as BigQuery writes it.
     *
     * <p>A repeated column is an array of its type, and saying otherwise costs the resolve of
     * everything under an {@code UNNEST}: unnesting a value the platform does not believe is an
     * array yields no element type, so a field read off the alias resolves to nothing at all.</p>
     */
    private String typeSpecification() {
        String base = isRecord()
                ? String.format("STRUCT<%s>", String.join(",", subFields.stream()
                        .map(child -> child.name() + " " + child.dasType().getDescription())
                        .toList()))
                : type;
        return isRepeated() ? String.format("ARRAY<%s>", base) : base;
    }

    public boolean isRecord() {
        return "RECORD".equals(type) || "STRUCT".equals(type);
    }

    public boolean isRepeated() {
        return "REPEATED".equals(mode);
    }

    @Override
    public String toString() {
        return "ColumnInfo{" +
                "name='" + name + '\'' +
                ", type='" + type + '\'' +
                ", mode='" + mode + '\'' +
                '}';
    }
}
