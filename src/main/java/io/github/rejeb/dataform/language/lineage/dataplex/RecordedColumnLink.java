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
package io.github.rejeb.dataform.language.lineage.dataplex;

import org.jetbrains.annotations.NotNull;

/**
 * A recorded flow of data from one top-level column to another.
 *
 * @param sourceFqn    the fully qualified name of the entity read
 * @param sourceColumn the column read
 * @param targetFqn    the fully qualified name of the entity written
 * @param targetColumn the column written
 * @param exactCopy    whether the value is copied unchanged
 */
public record RecordedColumnLink(@NotNull String sourceFqn, @NotNull String sourceColumn,
                                 @NotNull String targetFqn, @NotNull String targetColumn, boolean exactCopy) {
}
