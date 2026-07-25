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
package io.github.rejeb.dataform.language.evaluation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Outcome of evaluating a single expression. Exactly one of {@code value} and {@code error} is set.
 */
public record DataformEvaluationResult(@NotNull String source,
                                       @Nullable String value,
                                       @Nullable String error) {

    /**
     * Creates a successful result.
     */
    public static DataformEvaluationResult resolved(@NotNull String source, @NotNull String value) {
        return new DataformEvaluationResult(source, value, null);
    }

    /**
     * Creates a failed result. Failures are cached so the same expression is not retried on every pass.
     */
    public static DataformEvaluationResult failed(@NotNull String source, @NotNull String error) {
        return new DataformEvaluationResult(source, null, error);
    }

    /**
     * Tells whether a value could be computed.
     */
    public boolean isResolved() {
        return value != null;
    }
}
