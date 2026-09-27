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
package io.github.rejeb.dataform.language.diagnostics;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A Dataform compilation error of a file. The position the compiler reports is one of the
 * JavaScript it generates from the file, not of the file itself, so the record carries what the
 * error can be placed from, its stack and the file it was reported for, rather than a line.
 *
 * @param file             the file the error is shown in
 * @param message          the message
 * @param actionName       the action the error belongs to, or {@code null}
 * @param stack            the stack the error was thrown with, or {@code null}
 * @param reportedFileName the project-relative file the compiler reported the error for, or {@code null}
 */
public record CompilationDiagnostic(@NotNull VirtualFile file,
                                    @NotNull String message,
                                    @Nullable String actionName,
                                    @Nullable String stack,
                                    @Nullable String reportedFileName) {

    public CompilationDiagnostic(@NotNull VirtualFile file, @NotNull String message, @Nullable String actionName) {
        this(file, message, actionName, null, null);
    }
}
