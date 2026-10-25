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
package io.github.rejeb.dataform.language.unittest.execution.engine;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Result of one unit test. {@code expected} and {@code actual} hold both result sets rendered as
 * tables when the two queries ran, so they can be shown side by side.
 */
public record UnitTestOutcome(boolean passed,
                              @NotNull List<String> messages,
                              @Nullable String expected,
                              @Nullable String actual) {

    /**
     * Returns a successful outcome.
     */
    @NotNull
    public static UnitTestOutcome success() {
        return new UnitTestOutcome(true, List.of(), null, null);
    }

    /**
     * Returns a failed outcome with its messages and, when available, both result sets.
     */
    @NotNull
    public static UnitTestOutcome failed(@NotNull List<String> messages,
                                         @Nullable String expected,
                                         @Nullable String actual) {
        return new UnitTestOutcome(false, List.copyOf(messages), expected, actual);
    }

    /**
     * Returns the outcome of a test whose queries could not run, worded as the Dataform CLI does.
     */
    @NotNull
    public static UnitTestOutcome error(@NotNull String message) {
        return failed(List.of("Error thrown: " + message + "."), null, null);
    }
}
