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
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An in-memory {@link DataplexLineageClient}: links are declared with {@link #link}, every call is
 * logged, and an entity can be made to fail.
 */
final class FakeLineageClient implements DataplexLineageClient {

    private final List<RecordedLink> links = new ArrayList<>();
    private final Map<String, DataplexLineageException.Kind> failures = new LinkedHashMap<>();
    private final Map<String, List<RecordedProcess>> processes = new LinkedHashMap<>();
    private final Map<String, List<RecordedRun>> runs = new LinkedHashMap<>();
    private final List<RecordedColumnLink> columnLinks = new ArrayList<>();
    static final Instant RECENT = Instant.parse("2026-11-12T09:00:00Z");

    final List<String> calls = Collections.synchronizedList(new ArrayList<>());
    final List<LineageScope> scopes = Collections.synchronizedList(new ArrayList<>());
    final List<LineageScope> columnScopes = Collections.synchronizedList(new ArrayList<>());
    final AtomicInteger highestConcurrency = new AtomicInteger();
    private final AtomicInteger running = new AtomicInteger();
    private final Map<String, Long> delays = new LinkedHashMap<>();
    private DataplexLineageException.Kind columnFailure;
    private String newest;
    Runnable onStream = () -> {
    };
    Runnable onNewest = () -> {
    };

    static String fqn(String table) {
        return "bigquery:p.d." + table;
    }

    FakeLineageClient link(String source, String target) {
        return link(source, target, RECENT.minusSeconds(3_600), RECENT);
    }

    FakeLineageClient link(String source, String target, Instant first, Instant last) {
        links.add(new RecordedLink("links/" + links.size(), fqn(source), fqn(target), first, last));
        return this;
    }

    FakeLineageClient rawLink(String sourceFqn, String targetFqn) {
        links.add(new RecordedLink("links/" + links.size(), sourceFqn, targetFqn,
                RECENT.minusSeconds(3_600), RECENT));
        return this;
    }

    FakeLineageClient failOn(String table, DataplexLineageException.Kind kind) {
        failures.put(fqn(table), kind);
        return this;
    }

    FakeLineageClient process(String linkName, RecordedProcess process) {
        processes.computeIfAbsent(linkName, k -> new ArrayList<>()).add(process);
        return this;
    }

    FakeLineageClient runs(String processName, List<RecordedRun> processRuns) {
        runs.put(processName, processRuns);
        return this;
    }

    FakeLineageClient columnLink(String source, String sourceColumn, String target, String targetColumn) {
        columnLinks.add(new RecordedColumnLink(fqn(source), sourceColumn, fqn(target), targetColumn, false));
        return this;
    }

    FakeLineageClient exactColumnLink(String source, String sourceColumn, String target, String targetColumn) {
        columnLinks.add(new RecordedColumnLink(fqn(source), sourceColumn, fqn(target), targetColumn, true));
        return this;
    }

    FakeLineageClient failColumns(DataplexLineageException.Kind kind) {
        columnFailure = kind;
        return this;
    }

    FakeLineageClient delay(String key, long millis) {
        delays.put(key, millis);
        return this;
    }

    private void enter(String key) {
        highestConcurrency.accumulateAndGet(running.incrementAndGet(), Math::max);
        try {
            Long millis = delays.get(key);
            if (millis != null) Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            running.decrementAndGet();
        }
    }

    FakeLineageClient newest(String processName) {
        newest = processName;
        return this;
    }

    String linkName(String source, String target) {
        return links.stream().filter(l -> l.sourceFqn().equals(fqn(source)) && l.targetFqn().equals(fqn(target)))
                .findFirst().orElseThrow().name();
    }

    @Override
    public @NotNull List<RecordedLink> links(@NotNull LineageScope scope, @NotNull String entityFqn,
                                             @NotNull Direction direction) {
        calls.add(direction + " " + entityFqn);
        scopes.add(scope);
        enter(entityFqn);
        DataplexLineageException.Kind failure = failures.get(entityFqn);
        if (failure != null) throw new DataplexLineageException(failure, "failed on " + entityFqn, null);
        return links.stream()
                .filter(l -> (direction == Direction.UPSTREAM ? l.targetFqn() : l.sourceFqn()).equals(entityFqn))
                .toList();
    }

    @Override
    public @NotNull Map<String, List<String>> processNames(@NotNull LineageScope scope, @NotNull List<String> linkNames) {
        calls.add("PROCESSES " + linkNames);
        scopes.add(scope);
        Map<String, List<String>> byLink = new LinkedHashMap<>();
        for (String link : linkNames) {
            List<RecordedProcess> ofLink = processes.get(link);
            if (ofLink != null) byLink.put(link, ofLink.stream().map(RecordedProcess::name).distinct().toList());
        }
        return byLink;
    }

    @Override
    public @NotNull RecordedProcess process(@NotNull String processName) {
        calls.add("PROCESS " + processName);
        enter(processName);
        return processes.values().stream().flatMap(List::stream).filter(process -> process.name().equals(processName))
                .findFirst().orElseThrow();
    }

    @Override
    public @NotNull List<RecordedRun> runs(@NotNull String processName, int limit) {
        calls.add("RUNS " + processName);
        enter(processName);
        return runs.getOrDefault(processName, List.of()).stream().limit(limit).toList();
    }

    @Override
    public @NotNull List<RecordedColumnLink> columnStream(@NotNull LineageScope scope,
                                                          @NotNull Map<String, List<String>> rootColumns,
                                                          @NotNull Set<Direction> directions, int maxDepth) {
        calls.add("COLUMN STREAM " + EnumSet.copyOf(directions) + " " + rootColumns + " depth=" + maxDepth);
        columnScopes.add(scope);
        if (columnFailure != null) throw new DataplexLineageException(columnFailure, "failed on columns", null);
        List<RecordedColumnLink> found = new ArrayList<>();
        for (Direction direction : EnumSet.copyOf(directions)) {
            Set<String> visited = new HashSet<>();
            Set<String> frontier = new LinkedHashSet<>();
            rootColumns.forEach((fqn, columns) -> columns.forEach(column -> frontier.add(fqn + "#" + column)));
            visited.addAll(frontier);
            Set<RecordedColumnLink> emitted = new HashSet<>();
            for (int depth = 1; depth <= maxDepth && !frontier.isEmpty(); depth++) {
                Set<String> next = new LinkedHashSet<>();
                for (String column : frontier) {
                    for (RecordedColumnLink link : columnLinks) {
                        String near = direction == Direction.UPSTREAM
                                ? link.targetFqn() + "#" + link.targetColumn() : link.sourceFqn() + "#" + link.sourceColumn();
                        if (!near.equals(column) || !emitted.add(link)) continue;
                        found.add(link);
                        String far = direction == Direction.UPSTREAM
                                ? link.sourceFqn() + "#" + link.sourceColumn() : link.targetFqn() + "#" + link.targetColumn();
                        if (visited.add(far)) next.add(far);
                    }
                }
                frontier.clear();
                frontier.addAll(next);
            }
        }
        return found;
    }

    @Override
    public @NotNull StreamAnswer stream(@NotNull LineageScope scope, @NotNull List<String> rootFqns,
                                        @NotNull Set<Direction> directions, int maxDepth, @Nullable Instant since) {
        onStream.run();
        List<StreamedLink> found = new ArrayList<>();
        for (Direction direction : EnumSet.copyOf(directions)) {
            calls.add("STREAM " + direction + " " + rootFqns + " depth=" + maxDepth);
            scopes.add(scope);
            for (String root : rootFqns) {
                DataplexLineageException.Kind failure = failures.get(root);
                if (failure != null) throw new DataplexLineageException(failure, "failed on " + root, null);
            }
            Set<String> visited = new HashSet<>(rootFqns);
            Set<String> emitted = new HashSet<>();
            Set<String> frontier = new LinkedHashSet<>(rootFqns);
            for (int depth = 1; depth <= maxDepth && !frontier.isEmpty(); depth++) {
                Set<String> next = new LinkedHashSet<>();
                for (String entity : frontier) {
                    for (RecordedLink link : links) {
                        boolean near = (direction == Direction.UPSTREAM ? link.targetFqn() : link.sourceFqn()).equals(entity);
                        if (!near || (since != null && link.lastSeen() != null && link.lastSeen().isBefore(since))) continue;
                        if (!emitted.add(link.sourceFqn() + "\n" + link.targetFqn())) continue;
                        found.add(new StreamedLink(link.sourceFqn(), link.targetFqn(), depth, direction));
                        String far = direction == Direction.UPSTREAM ? link.sourceFqn() : link.targetFqn();
                        if (visited.add(far)) next.add(far);
                    }
                }
                frontier = next;
            }
        }
        return new StreamAnswer(found, false);
    }

    @Override
    public @Nullable String newestProcess(@NotNull LineageScope scope) {
        calls.add("NEWEST");
        scopes.add(scope);
        onNewest.run();
        return newest;
    }
}
