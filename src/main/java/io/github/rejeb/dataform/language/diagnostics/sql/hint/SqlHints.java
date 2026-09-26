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
package io.github.rejeb.dataform.language.diagnostics.sql.hint;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Finds the hint for a SQL error among the hint families.
 */
public final class SqlHints {

    private static final List<SqlHintProvider> PROVIDERS = List.of(
            new NameTypoHints(),
            new TableReferenceHints(),
            new QueryStructureHints(),
            new SyntaxHints());

    private SqlHints() {
    }

    /**
     * The hint of the family the error belongs to, or {@link SqlHint#NONE} when none explains it.
     */
    public static @NotNull SqlHint hintFor(@NotNull SqlErrorContext context) {
        for (SqlHintProvider provider : PROVIDERS) {
            if (provider.kinds().contains(context.error().kind())) return provider.hint(context);
        }
        return SqlHint.NONE;
    }

    /**
     * Names quoted and joined for a sentence: 'a', 'b' or 'c'.
     */
    public static @NotNull String quoted(@NotNull List<String> names) {
        List<String> quoted = names.stream().map(name -> "'" + name + "'").toList();
        if (quoted.size() == 1) return quoted.getFirst();
        return String.join(", ", quoted.subList(0, quoted.size() - 1)) + " or " + quoted.getLast();
    }
}
