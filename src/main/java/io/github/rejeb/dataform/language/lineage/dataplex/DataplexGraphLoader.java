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
import org.jetbrains.annotations.Nullable;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * Loads the recorded lineage around tables. The whole repository is read with one multi-level
 * search each way; one level more than what is kept is read, so that what is hidden behind the
 * last level can be counted. Expanding and including an entity read one level at a time.
 */
public final class DataplexGraphLoader {

    /** A load never holds more entities than this. */
    public static final int MAX_ENTITIES = 200;

    /** The levels a full load reads each way; the last one is only counted. */
    public static final int FULL_DEPTH = 3;

    private final DataplexLineageClient client;
    private final LineageScope scope;
    private final Clock clock;
    private final ParallelCalls calls;

    public DataplexGraphLoader(@NotNull DataplexLineageClient client, @NotNull LineageScope scope, @NotNull Clock clock,
                               @NotNull ParallelCalls calls) {
        this.client = client;
        this.scope = scope;
        this.clock = clock;
        this.calls = calls;
    }


    /**
     * Loads every root and the entities up to two levels away from the nearest root, each way. Roots
     * are never left out; at most {@link #MAX_ENTITIES} other entities are kept, nearest first.
     *
     * @param since when set, only the links whose last event is at or after it
     * @throws DataplexLineageException when the search fails
     */
    public @NotNull DataplexSnapshot loadAll(@NotNull List<String> rootFqns, @Nullable Instant since) {
        Loaded loaded = new Loaded(rootFqns, clock.instant());
        loaded.upstreamRead.addAll(rootFqns);
        if (rootFqns.isEmpty()) return loaded.snapshot();
        StreamAnswer answer = client.stream(scope, rootFqns, EnumSet.allOf(Direction.class), FULL_DEPTH, since);
        List<StreamedLink> kept = answer.links().stream()
                .filter(link -> link.depth() < FULL_DEPTH && !link.sourceFqn().equals(link.targetFqn()))
                .sorted(Comparator.comparingInt(StreamedLink::depth))
                .toList();
        int outside = 0;
        for (StreamedLink link : kept) {
            String far = link.farEnd();
            if (loaded.entities.contains(far)) continue;
            if (outside >= MAX_ENTITIES) {
                loaded.truncated = true;
                continue;
            }
            loaded.entities.add(far);
            outside++;
        }
        for (StreamedLink link : kept) {
            if (link.direction() == Direction.UPSTREAM && loaded.entities.contains(link.farEnd())) {
                loaded.upstreamRead.add(link.farEnd());
            }
            if (answer.truncated() && link.depth() == FULL_DEPTH - 1 && loaded.entities.contains(link.farEnd())) {
                loaded.unqueried(link.direction()).add(link.farEnd());
            }
        }
        for (StreamedLink link : answer.links()) {
            if (link.sourceFqn().equals(link.targetFqn())) continue;
            loaded.links.putIfAbsent(pairOf(link.sourceFqn(), link.targetFqn()),
                    RecordedLink.streamed(link.sourceFqn(), link.targetFqn()));
        }
        return loaded.snapshot();
    }

    /**
     * Adds an entity with its links one level away each way.
     *
     * @return the larger snapshot, or the given one when the entity is already loaded
     */
    public @NotNull DataplexSnapshot include(@NotNull DataplexSnapshot current, @NotNull String entityFqn) {
        Loaded loaded = new Loaded(current);
        if (!loaded.entities.add(entityFqn)) return current;
        List<Callable<List<RecordedLink>>> asks = List.of(
                () -> fetch(entityFqn, Direction.UPSTREAM), () -> fetch(entityFqn, Direction.DOWNSTREAM));
        List<List<RecordedLink>> answers = calls.all(asks);
        merge(loaded, entityFqn, Direction.UPSTREAM, answers.get(0));
        merge(loaded, entityFqn, Direction.DOWNSTREAM, answers.get(1));
        return loaded.snapshot();
    }

    /**
     * Loads what is hidden on one side of a loaded entity, and counts what is hidden behind the
     * entities this adds.
     *
     * @return the larger snapshot, or the given one when nothing is hidden on that side
     */
    public @NotNull DataplexSnapshot expand(@NotNull DataplexSnapshot current, @NotNull String entityFqn,
                                            @NotNull Direction direction) {
        Loaded loaded = new Loaded(current);
        boolean queried = loaded.unqueried(direction).remove(entityFqn);
        if (queried) query(loaded, entityFqn, direction, false);
        List<String> added = new ArrayList<>();
        for (RecordedLink link : loaded.links.values()) {
            if (!near(link, direction).equals(entityFqn)) continue;
            String neighbour = far(link, direction);
            if (added.size() < MAX_ENTITIES && loaded.entities.add(neighbour)) added.add(neighbour);
        }
        if (!queried && added.isEmpty()) return current;
        queryAll(loaded, added, direction);
        return loaded.snapshot();
    }

    private void queryAll(@NotNull Loaded loaded, @NotNull List<String> entities, @NotNull Direction direction) {
        List<Callable<List<RecordedLink>>> asks = new ArrayList<>();
        for (String entity : entities) asks.add(() -> fetch(entity, direction));
        List<List<RecordedLink>> answers = calls.all(asks);
        for (int i = 0; i < entities.size(); i++) merge(loaded, entities.get(i), direction, answers.get(i));
    }

    private @Nullable List<RecordedLink> fetch(@NotNull String entity, @NotNull Direction direction) {
        try {
            return client.links(scope, entity, direction);
        } catch (DataplexLineageException e) {
            return null;
        }
    }


    private @NotNull List<RecordedLink> query(@NotNull Loaded loaded, @NotNull String entity,
                                              @NotNull Direction direction, boolean failOnError) {
        List<RecordedLink> found;
        try {
            found = client.links(scope, entity, direction);
        } catch (DataplexLineageException e) {
            if (failOnError) throw e;
            found = null;
        }
        return merge(loaded, entity, direction, found);
    }

    private static @NotNull List<RecordedLink> merge(@NotNull Loaded loaded, @NotNull String entity,
                                                     @NotNull Direction direction, @Nullable List<RecordedLink> found) {
        if (found == null) {
            loaded.unreachable.add(entity);
            return List.of();
        }
        if (direction == Direction.UPSTREAM) loaded.upstreamRead.add(entity);
        List<RecordedLink> kept = new ArrayList<>();
        for (RecordedLink link : found) {
            if (link.sourceFqn().equals(link.targetFqn())) continue;
            loaded.links.putIfAbsent(keyOf(link), link);
            kept.add(link);
        }
        return kept;
    }

    private static @NotNull String keyOf(@NotNull RecordedLink link) {
        return pairOf(link.sourceFqn(), link.targetFqn());
    }

    private static @NotNull String pairOf(@NotNull String sourceFqn, @NotNull String targetFqn) {
        return sourceFqn + '\n' + targetFqn;
    }

    private static @NotNull String near(@NotNull RecordedLink link, @NotNull Direction direction) {
        return direction == Direction.UPSTREAM ? link.targetFqn() : link.sourceFqn();
    }

    private static @NotNull String far(@NotNull RecordedLink link, @NotNull Direction direction) {
        return direction == Direction.UPSTREAM ? link.sourceFqn() : link.targetFqn();
    }

    private static final class Loaded {
        private final List<String> rootFqns;
        private final Instant loadedAt;
        private final Set<String> entities = new LinkedHashSet<>();
        private final Map<String, RecordedLink> links = new LinkedHashMap<>();
        private final Set<String> unqueriedUpstream = new LinkedHashSet<>();
        private final Set<String> unqueriedDownstream = new LinkedHashSet<>();
        private final Set<String> unreachable = new LinkedHashSet<>();
        private final Set<String> upstreamRead = new LinkedHashSet<>();
        private boolean truncated;

        Loaded(@NotNull Collection<String> rootFqns, @NotNull Instant loadedAt) {
            this.rootFqns = List.copyOf(new LinkedHashSet<>(rootFqns));
            this.loadedAt = loadedAt;
            entities.addAll(this.rootFqns);
        }

        Loaded(@NotNull DataplexSnapshot snapshot) {
            this(snapshot.rootFqns(), snapshot.loadedAt());
            entities.addAll(snapshot.entities());
            for (RecordedLink link : snapshot.links()) links.put(keyOf(link), link);
            for (RecordedLink link : snapshot.pendingLinks()) links.put(keyOf(link), link);
            unqueriedUpstream.addAll(snapshot.unqueriedUpstream());
            unqueriedDownstream.addAll(snapshot.unqueriedDownstream());
            unreachable.addAll(snapshot.unreachable());
            upstreamRead.addAll(snapshot.upstreamRead());
            truncated = snapshot.truncated();
        }

        @NotNull Set<String> unqueried(@NotNull Direction direction) {
            return direction == Direction.UPSTREAM ? unqueriedUpstream : unqueriedDownstream;
        }

        @NotNull DataplexSnapshot snapshot() {
            List<RecordedLink> joined = new ArrayList<>();
            List<RecordedLink> pending = new ArrayList<>();
            for (RecordedLink link : links.values()) {
                boolean both = entities.contains(link.sourceFqn()) && entities.contains(link.targetFqn());
                (both ? joined : pending).add(link);
            }
            return new DataplexSnapshot(new LinkedHashSet<>(rootFqns), entities, joined, pending, unqueriedUpstream, unqueriedDownstream,
                    unreachable, upstreamRead, truncated, loadedAt);
        }
    }
}
