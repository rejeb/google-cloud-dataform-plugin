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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Why the dry-run of an action failed: the message BigQuery answered with and, when the plugin
 * built the query itself, the query that was sent, traced back to the compiled texts it is made of.
 */
public record DryRunFailure(@NotNull String message, @Nullable MappedText query) {

    /**
     * Whether another failure says the same about the same query, so that nothing reading the
     * failures has to be told again.
     */
    public boolean sameAs(@Nullable DryRunFailure other) {
        return other != null
                && message.equals(other.message)
                && Objects.equals(textOf(query), textOf(other.query));
    }

    private static @Nullable String textOf(@Nullable MappedText query) {
        return query == null ? null : query.text();
    }
}
