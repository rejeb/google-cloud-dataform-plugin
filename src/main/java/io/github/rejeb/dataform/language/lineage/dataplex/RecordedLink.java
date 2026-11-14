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
import org.jetbrains.annotations.Nullable;

import java.time.Instant;

/**
 * A recorded flow of data from one entity to another.
 *
 * @param name      the resource name of the link, empty when the link was found by a multi-level search
 * @param sourceFqn the fully qualified name of the entity the data was read from
 * @param targetFqn the fully qualified name of the entity the data was written to
 * @param firstSeen the time of the first event that recorded the flow, {@code null} when not read
 * @param lastSeen  the time of the last event that recorded the flow, {@code null} when not read
 */
public record RecordedLink(@NotNull String name, @NotNull String sourceFqn, @NotNull String targetFqn,
                           @Nullable Instant firstSeen, @Nullable Instant lastSeen) {

    /**
     * @return a link found by a multi-level search, which gives neither its name nor its times
     */
    public static @NotNull RecordedLink streamed(@NotNull String sourceFqn, @NotNull String targetFqn) {
        return new RecordedLink("", sourceFqn, targetFqn, null, null);
    }
}
