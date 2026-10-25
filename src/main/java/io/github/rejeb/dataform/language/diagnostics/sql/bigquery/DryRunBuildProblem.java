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
package io.github.rejeb.dataform.language.diagnostics.sql.bigquery;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A failed dry-run of an action, as the Build tool window lists it.
 *
 * @param actionFullName the full name of the action whose query failed
 * @param file           the source file of the action, {@code null} when it cannot be found
 * @param line           the zero-based line of the error in the file, 0 when it cannot be placed
 * @param column         the zero-based column of the error in the file, 0 when it cannot be placed
 * @param message        what BigQuery answered, without the position in the query sent
 * @param details        the whole answer of BigQuery
 * @param subject        the name the error is about, {@code null} when it names none
 */
public record DryRunBuildProblem(@NotNull String actionFullName,
                                 @Nullable VirtualFile file,
                                 int line,
                                 int column,
                                 @NotNull String message,
                                 @NotNull String details,
                                 @Nullable String subject) {
}
