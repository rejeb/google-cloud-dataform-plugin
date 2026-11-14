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
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads the lineage recorded by Knowledge Catalog (the Data Lineage API). Every method blocks on
 * the network and must be called off the event dispatch thread; every failure is a
 * {@link DataplexLineageException}.
 */
public interface DataplexLineageClient {

    /** The side of an entity a search looks at. */
    enum Direction { UPSTREAM, DOWNSTREAM }

    /**
     * @param entityFqn the fully qualified name of an entity
     * @param direction {@code UPSTREAM} for the links into the entity, {@code DOWNSTREAM} for the links out of it
     * @return the links one level away from the entity
     */
    @NotNull List<RecordedLink> links(@NotNull LineageScope scope, @NotNull String entityFqn, @NotNull Direction direction);

    /**
     * @param linkNames the resource names of links
     * @return the resource names of the processes of each link, by link name; a link without a process has no entry
     */
    @NotNull Map<String, List<String>> processNames(@NotNull LineageScope scope, @NotNull List<String> linkNames);

    /**
     * @param processName the resource name of a process
     * @return the process
     */
    @NotNull RecordedProcess process(@NotNull String processName);

    /**
     * @param processName the resource name of a process
     * @param limit       the largest number of runs to return
     * @return the runs of the process, newest first
     */
    @NotNull List<RecordedRun> runs(@NotNull String processName, int limit);

    /**
     * Searches the column links several levels around many columns at once.
     *
     * @param rootColumns the columns to start from, by the fully qualified name of their entity
     * @param directions  the directions to search
     * @param maxDepth    the number of levels to read away from the nearest root
     * @return the links between two top-level columns found
     */
    @NotNull List<RecordedColumnLink> columnStream(@NotNull LineageScope scope, @NotNull Map<String, List<String>> rootColumns,
                                                   @NotNull Set<Direction> directions, int maxDepth);

    /**
     * Searches several levels around many roots at once.
     *
     * @param rootFqns   the fully qualified names of the entities to start from
     * @param directions the directions to search
     * @param maxDepth   the number of levels to read away from the nearest root
     * @param since      when set, only the links whose last event is at or after it
     * @return the links found, each with its depth and the direction that found it
     */
    @NotNull StreamAnswer stream(@NotNull LineageScope scope, @NotNull List<String> rootFqns,
                                 @NotNull Set<Direction> directions, int maxDepth, @Nullable Instant since);

    /**
     * @return the resource name of the process recorded last in that scope, or {@code null} when none is
     */
    @Nullable String newestProcess(@NotNull LineageScope scope);
}
