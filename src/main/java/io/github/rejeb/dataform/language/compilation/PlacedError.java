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
package io.github.rejeb.dataform.language.compilation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Where a compilation error is in the project as it reads now.
 *
 * @param path         the project-relative path of the file the error is reported for
 * @param line         the zero-based line of the placed range
 * @param column       the zero-based column the placed range starts at
 * @param length       the length of the placed range
 * @param fileText     the text of the file the range is in: the include when the error is thrown in one
 * @param hint         how to fix the error, when known
 * @param thrownInPath the project-relative path of the include the error is thrown in, or {@code null}
 *                     when it is placed in the file it is reported for
 */
public record PlacedError(@NotNull String path, int line, int column, int length, @NotNull String fileText,
                          @Nullable String hint, @Nullable String thrownInPath) {

    /**
     * The path of the file the range is in.
     */
    public @NotNull String rangePath() {
        return thrownInPath != null ? thrownInPath : path;
    }
}
