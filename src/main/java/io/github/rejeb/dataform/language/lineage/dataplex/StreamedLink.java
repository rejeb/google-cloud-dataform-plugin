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

import io.github.rejeb.dataform.language.lineage.dataplex.DataplexLineageClient.Direction;
import org.jetbrains.annotations.NotNull;

/**
 * A table link found by a multi-level search: no name and no times, only its ends and how far it
 * lies from the nearest root of the search.
 *
 * @param sourceFqn the fully qualified name of the entity read
 * @param targetFqn the fully qualified name of the entity written
 * @param depth     1 for a link touching a root, 2 for the next level, and so on
 * @param direction the direction of the search that found it
 */
public record StreamedLink(@NotNull String sourceFqn, @NotNull String targetFqn, int depth,
                           @NotNull Direction direction) {

    /**
     * @return the end of the link away from the roots: the source upstream, the target downstream
     */
    public @NotNull String farEnd() {
        return direction == Direction.UPSTREAM ? sourceFqn : targetFqn;
    }

    /**
     * @return the end of the link toward the roots
     */
    public @NotNull String nearEnd() {
        return direction == Direction.UPSTREAM ? targetFqn : sourceFqn;
    }
}
