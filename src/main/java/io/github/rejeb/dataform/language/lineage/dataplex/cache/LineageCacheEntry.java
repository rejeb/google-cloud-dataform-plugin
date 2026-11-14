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

import io.github.rejeb.dataform.language.lineage.dataplex.DataplexSnapshot;
import io.github.rejeb.dataform.language.lineage.dataplex.LineageScope;
import io.github.rejeb.dataform.language.lineage.dataplex.TimeRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The recorded lineage of a repository as it was last read, for one scope and time range.
 *
 * @param scope      where the lineage was read
 * @param range      how far back it was read
 * @param snapshot   what was read, expansions included
 * @param targets    the repository tables it was read for
 * @param marker     the newest process recorded in the scope when the read started, {@code null} when none was
 * @param expansions the expanders clicked since, in order
 */
public record LineageCacheEntry(@NotNull LineageScope scope, @NotNull TimeRange range, @NotNull DataplexSnapshot snapshot,
                                @NotNull String targets, @Nullable String marker, @NotNull List<Expansion> expansions) {

    public LineageCacheEntry {
        Objects.requireNonNull(scope);
        Objects.requireNonNull(range);
        Objects.requireNonNull(snapshot);
        Objects.requireNonNull(targets);
        expansions = List.copyOf(expansions);
    }

    /**
     * @return the same entry with another snapshot
     */
    public @NotNull LineageCacheEntry withSnapshot(@NotNull DataplexSnapshot snapshot) {
        return new LineageCacheEntry(scope, range, snapshot, targets, marker, expansions);
    }

    /**
     * @return the same entry with one more expansion, unless it is already there
     */
    public @NotNull LineageCacheEntry withExpansion(@NotNull Expansion expansion) {
        if (expansions.contains(expansion)) return this;
        List<Expansion> more = new ArrayList<>(expansions);
        more.add(expansion);
        return new LineageCacheEntry(scope, range, snapshot, targets, marker, more);
    }
}
