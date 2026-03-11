/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
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
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;


public record ColumnInfo(
        @NotNull String name,
        @NotNull String type,
        @NotNull String mode,
        @Nullable String description,
        @NotNull List<ColumnInfo> subFields
) {
    public ColumnInfo(@NotNull String name, @NotNull String type, @NotNull String mode,@Nullable String description) {
        this(name, type, mode,description, Collections.emptyList());
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
