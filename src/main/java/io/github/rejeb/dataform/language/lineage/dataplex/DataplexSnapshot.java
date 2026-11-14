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

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The part of the recorded lineage that was loaded around a set of tables.
 *
 * @param rootFqns            the fully qualified names of the entities the load started from, in order
 * @param entities            the loaded entities, the roots included
 * @param links               the links between two loaded entities
 * @param pendingLinks        the links with one end outside the loaded entities: what is hidden behind an entity
 * @param unqueriedUpstream   the loaded entities whose upstream side was not asked for, because the load stopped
 * @param unqueriedDownstream the loaded entities whose downstream side was not asked for, because the load stopped
 * @param unreachable         the loaded entities whose links could not be read
 * @param upstreamRead        the loaded entities whose upstream links were read: only for those is a missing source known to be missing
 * @param truncated           whether the load stopped at the largest number of entities
 * @param loadedAt            when the load was made
 */
public record DataplexSnapshot(@NotNull Set<String> rootFqns, @NotNull Set<String> entities, @NotNull List<RecordedLink> links,
                               @NotNull List<RecordedLink> pendingLinks,
                               @NotNull Set<String> unqueriedUpstream, @NotNull Set<String> unqueriedDownstream,
                               @NotNull Set<String> unreachable, @NotNull Set<String> upstreamRead, boolean truncated,
                               @NotNull Instant loadedAt) {

    public DataplexSnapshot {
        rootFqns = Collections.unmodifiableSet(new LinkedHashSet<>(rootFqns));
        entities = Collections.unmodifiableSet(new LinkedHashSet<>(entities));
        links = List.copyOf(links);
        pendingLinks = List.copyOf(pendingLinks);
        unqueriedUpstream = Set.copyOf(unqueriedUpstream);
        unqueriedDownstream = Set.copyOf(unqueriedDownstream);
        unreachable = Set.copyOf(unreachable);
        upstreamRead = Set.copyOf(upstreamRead);
    }

    /**
     * @return the number of entities hidden upstream of the given one, {@code -1} when its
     * upstream side was not asked for
     */
    public int hiddenUpstream(@NotNull String fqn) {
        if (unqueriedUpstream.contains(fqn)) return -1;
        Set<String> sources = new HashSet<>();
        for (RecordedLink link : pendingLinks) {
            if (link.targetFqn().equals(fqn)) sources.add(link.sourceFqn());
        }
        return sources.size();
    }

    /**
     * @return the number of entities hidden downstream of the given one, {@code -1} when its
     * downstream side was not asked for
     */
    public int hiddenDownstream(@NotNull String fqn) {
        if (unqueriedDownstream.contains(fqn)) return -1;
        Set<String> targets = new HashSet<>();
        for (RecordedLink link : pendingLinks) {
            if (link.sourceFqn().equals(fqn)) targets.add(link.targetFqn());
        }
        return targets.size();
    }
}
