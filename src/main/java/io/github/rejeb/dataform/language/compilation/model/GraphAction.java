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
package io.github.rejeb.dataform.language.compilation.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * An action of the compiled graph, whatever its kind, as the features about tables and columns see it.
 *
 * @param target       the table or view the action writes or declares
 * @param kind         the table type for a table, otherwise {@code operation}, {@code assertion} or
 *                     {@code declaration}
 * @param fileName     the project-relative file the action was compiled from
 * @param dependencies the actions it reads
 * @param buildsTable  whether the action builds its target from a query of its file: a table, or an
 *                     operation with output
 */
public record GraphAction(@NotNull Target target, @NotNull String kind, @Nullable String fileName,
                          @NotNull List<Target> dependencies, boolean buildsTable) {
}
