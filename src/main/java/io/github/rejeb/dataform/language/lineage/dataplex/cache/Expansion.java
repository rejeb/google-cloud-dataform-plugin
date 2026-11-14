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
package io.github.rejeb.dataform.language.lineage.dataplex.cache;

import io.github.rejeb.dataform.language.lineage.dataplex.DataplexLineageClient.Direction;
import org.jetbrains.annotations.NotNull;

/**
 * An expander the user clicked: one side of an entity loaded beyond the full load.
 *
 * @param fqn       the fully qualified name of the entity
 * @param direction the side loaded
 */
public record Expansion(@NotNull String fqn, @NotNull Direction direction) {
}
