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

import io.github.rejeb.dataform.language.util.DataformPaths;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A Dataform compilation error, taken apart.
 *
 * @param kind    what the error is about
 * @param message the message on one line, without the documentation link it may end with
 * @param name    the name, property or action the message is about, or {@code null}
 * @param frames  the frames of its stack that name a file, innermost first
 * @param snippet the source line a syntax error quotes, or {@code null}
 */
public record ParsedCompilationError(@NotNull CompilationErrorKind kind,
                                     @NotNull String message,
                                     @Nullable String name,
                                     @NotNull List<StackFrame> frames,
                                     @Nullable SourceSnippet snippet) {

    public ParsedCompilationError {
        frames = List.copyOf(frames);
    }

    /**
     * The index of the first frame running in a file, or {@code -1} when none does.
     */
    public int indexOfFrameIn(@NotNull String filePath) {
        for (int i = 0; i < frames.size(); i++) {
            if (DataformPaths.pointsTo(filePath, frames.get(i).path())) return i;
        }
        return -1;
    }

    /**
     * The first frame running in a file, or {@code null} when none does.
     */
    public @Nullable StackFrame frameIn(@NotNull String filePath) {
        int index = indexOfFrameIn(filePath);
        return index < 0 ? null : frames.get(index);
    }
}
