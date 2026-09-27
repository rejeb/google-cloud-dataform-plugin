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
package io.github.rejeb.dataform.language.schema.sql;

import io.github.rejeb.dataform.language.util.MappedText;
import io.github.rejeb.dataform.language.util.Utils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The text of a dry-run, traced back to the compiled texts it is made of: the main query of the
 * action, and the read-only pre-operations put in front of it.
 */
public final class DryRunQueryText {

    public static final String MAIN_QUERY = "main_query";
    public static final String PRE_OPERATIONS = "pre_operations";

    private DryRunQueryText() {
    }

    /**
     * The mapped counterpart of {@link Utils#withPreOperations}: the same text, in which the
     * pre-operations are traced to a source of their own.
     */
    public static @NotNull MappedText withPreOperations(@Nullable List<String> preOperations,
                                                        @NotNull MappedText query) {
        if (preOperations == null || preOperations.isEmpty()) return query;
        String joined = Utils.joinPreOperations(preOperations);
        return MappedText.builder()
                .source(PRE_OPERATIONS, joined)
                .copy(PRE_OPERATIONS, 0, joined.length())
                .synthetic("\n")
                .append(query)
                .synthetic(";")
                .build();
    }
}
