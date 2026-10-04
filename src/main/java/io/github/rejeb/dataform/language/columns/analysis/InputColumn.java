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
package io.github.rejeb.dataform.language.columns.analysis;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Set;

/**
 * A column an output column is built from.
 *
 * @param excluded for a star, the lower-case names its {@code EXCEPT} and {@code REPLACE} lists take
 *                 out of it, which the star does not carry on; empty otherwise
 */
public record InputColumn(@Nullable String sourceAlias,
                          @NotNull String columnName,
                          @NotNull Confidence kind,
                          boolean star,
                          @NotNull Set<String> excluded) {

    public InputColumn(@Nullable String sourceAlias, @NotNull String columnName,
                       @NotNull Confidence kind, boolean star) {
        this(sourceAlias, columnName, kind, star, Set.of());
    }

    /**
     * Whether a star carries on the column of that name, which it does unless its {@code EXCEPT}
     * or {@code REPLACE} list names it.
     */
    public boolean carries(@NotNull String name) {
        return !excluded.contains(name.replace("`", "").toLowerCase(Locale.ROOT));
    }
}
