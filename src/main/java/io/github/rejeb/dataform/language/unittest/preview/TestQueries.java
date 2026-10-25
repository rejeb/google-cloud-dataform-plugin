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
package io.github.rejeb.dataform.language.unittest.preview;

import io.github.rejeb.dataform.language.compilation.model.CompiledTest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class TestQueries {

    public record Query(@NotNull String label, @NotNull String sql) {
    }

    private TestQueries() {
    }

    /**
     * Returns the queries of a unit test that can be executed on their own: the tested dataset on the
     * input rows, then the expected output.
     */
    @NotNull
    public static List<Query> of(@Nullable CompiledTest test) {
        if (test == null) {
            return List.of();
        }
        List<Query> queries = new ArrayList<>();
        if (!test.getTestQuery().isBlank()) {
            queries.add(new Query(test.getName() + " (test query)", test.getTestQuery()));
        }
        if (!test.getExpectedOutputQuery().isBlank()) {
            queries.add(new Query(test.getName() + " (expected output)", test.getExpectedOutputQuery()));
        }
        return queries;
    }
}
