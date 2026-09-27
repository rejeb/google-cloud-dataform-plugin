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
package io.github.rejeb.dataform.language.diagnostics.compile;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A frame of the stack a compilation error was thrown with.
 *
 * @param function the function the frame runs, as the stack writes it, or {@code null}
 * @param path     the file, relative to the Dataform project when that could be told
 * @param line     the one-based line
 * @param column   the one-based column
 */
public record StackFrame(@Nullable String function, @NotNull String path, int line, int column) {

    /**
     * The function without its receiver: {@code broken} for {@code Object.broken}.
     */
    public @Nullable String functionName() {
        return function == null ? null : function.substring(function.lastIndexOf('.') + 1);
    }
}
