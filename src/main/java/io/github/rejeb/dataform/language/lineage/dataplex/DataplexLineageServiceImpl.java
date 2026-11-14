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

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import io.github.rejeb.dataform.language.columns.model.ColumnRef;
import io.github.rejeb.dataform.language.compilation.model.CompiledAssertion;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.lineage.dataplex.DataplexLineageClient.Direction;
import io.github.rejeb.dataform.language.lineage.dataplex.cache.DataplexLineageCache;
import io.github.rejeb.dataform.language.lineage.dataplex.cache.DataplexLineageCacheImpl;
import io.github.rejeb.dataform.language.lineage.dataplex.cache.Expansion;
import io.github.rejeb.dataform.language.lineage.dataplex.cache.LineageCacheEntry;
import io.github.rejeb.dataform.language.lineage.extractor.LineageExtractorImpl;
import io.github.rejeb.dataform.language.lineage.graph.LineageGraph;
import io.github.rejeb.dataform.language.lineage.graph.LineageNode;
import io.github.rejeb.dataform.language.service.DataformProjectDefaults;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Default {@link DataplexLineageService}. The recorded lineage of the repository is kept in the
 * project cache; while a lineage view shows it, its decoded view and what is asked about it
 * (freshness, link details, runs) are held in memory, and dropped when no view shows it.
 */
public final class DataplexLineageServiceImpl implements DataplexLineageService {

    static final int TRACE_DEPTH = 5;
    static final int RUNS_PER_PROCESS = 10;
    static final int RUNS_SHOWN = 20;
    private static final String DEFAULT_LOCATION = "us";

    private final DataplexLineageClient client;
    private final Clock clock;
    private final Function<CompiledGraph, DataformProjectDefaults> defaults;
    private final ParallelCalls calls;
    private final DataplexLineageCache cache;

    private final Object lock = new Object();
    private final Object commits = new Object();
    private final Map<EntryKey, Held> held = new HashMap<>();
    private final Map<EntryKey, List<Consumer<DataplexView>>> shown = new HashMap<>();
    private final Map<EntryKey, CompletableFuture<String>> checks = new HashMap<>();
    private final Map<EntryKey, CompletableFuture<DataplexView>> reloads = new HashMap<>();
    private final Map<EntryKey, Checked> checked = new HashMap<>();
    private final Map<String, Object> answers = new HashMap<>();

    public DataplexLineageServiceImpl(@NotNull Project project) {
        this(new DataplexLineageClientImpl(), Clock.systemUTC(), compiled -> ReadAction.computeBlocking(
                () -> DataformProjectDefaults.of(project, null, compiled)), DataplexLineageCache.getInstance(project),
                ParallelCalls.shared());
    }

    DataplexLineageServiceImpl(@NotNull DataplexLineageClient client, @NotNull Clock clock,
                               @NotNull Function<CompiledGraph, DataformProjectDefaults> defaults) {
        this(client, clock, defaults, new DataplexLineageCacheImpl(), ParallelCalls.direct());
    }

    DataplexLineageServiceImpl(@NotNull DataplexLineageClient client, @NotNull Clock clock,
                               @NotNull Function<CompiledGraph, DataformProjectDefaults> defaults,
                               @NotNull ParallelCalls calls) {
        this(client, clock, defaults, new DataplexLineageCacheImpl(), calls);
    }

    DataplexLineageServiceImpl(@NotNull DataplexLineageClient client, @NotNull Clock clock,
                               @NotNull Function<CompiledGraph, DataformProjectDefaults> defaults,
                               @NotNull DataplexLineageCache cache, @NotNull ParallelCalls calls) {
        this.client = client;
        this.clock = clock;
        this.defaults = defaults;
        this.cache = cache;
        this.calls = calls;
    }

    @Override
    public @Nullable DataplexView cachedView(@NotNull CompiledGraph compiled, @NotNull TimeRange range) {
        Repository repository = repositoryOf(compiled);
        EntryKey key = EntryKey.of(repository.scope(), range);
        LineageCacheEntry entry;
        synchronized (lock) {
            Held current = held.get(key);
            if (current != null && current.targets().equals(repository.targets())) return current.view();
            entry = current == null ? null : current.entry();
        }
        if (entry == null) entry = cache.get(repository.scope(), range);
        if (entry == null) return null;
        DataplexView view = viewOf(entry, repository);
        synchronized (lock) {
            hold(key, new Held(view, entry, repository.targets()));
        }
        return view;
    }

    @Override
    public boolean needsReload(@NotNull CompiledGraph compiled, @NotNull TimeRange range) {
        Repository repository = repositoryOf(compiled);
        EntryKey key = EntryKey.of(repository.scope(), range);
        LineageCacheEntry entry = entryOf(key, repository.scope(), range);
        if (entry == null || !entry.targets().equals(repository.targets())) return true;
        String newest = shared(checks, key, () -> client.newestProcess(repository.scope()));
        if (Objects.equals(newest, entry.marker())) return false;
        synchronized (lock) {
            checked.put(key, new Checked(newest));
        }
        return true;
    }

    @Override
    public @NotNull DataplexView reload(@NotNull CompiledGraph compiled, @NotNull TimeRange range) {
        Repository repository = repositoryOf(compiled);
        EntryKey key = EntryKey.of(repository.scope(), range);
        DataplexView view = shared(reloads, key, () -> readAll(repository, range, key));
        if (targetsOf(view.repository()).equals(repository.targets())) return view;
        return shared(reloads, key, () -> readAll(repository, range, key));
    }

    private @NotNull DataplexView readAll(@NotNull Repository repository, @NotNull TimeRange range, @NotNull EntryKey key) {
        Checked known;
        synchronized (lock) {
            known = checked.remove(key);
        }
        String marker = known != null ? known.marker() : client.newestProcess(repository.scope());
        DataplexGraphLoader loader = new DataplexGraphLoader(inRange(range), repository.scope(), clock, calls);
        DataplexSnapshot snapshot = loader.loadAll(repository.roots(), clock.instant().minus(range.length()));
        LineageCacheEntry previous = entryOf(key, repository.scope(), range);
        List<Expansion> expansions = previous == null ? List.of() : previous.expansions();
        for (Expansion expansion : expansions) snapshot = loader.expand(snapshot, expansion.fqn(), expansion.direction());
        LineageCacheEntry read = new LineageCacheEntry(repository.scope(), range, snapshot, repository.targets(), marker,
                expansions);
        DataplexView view = viewOf(read, repository);
        List<Consumer<DataplexView>> receivers;
        synchronized (commits) {
            LineageCacheEntry entry = withExpansionsOf(read, entryOf(key, repository.scope(), range));
            cache.put(entry);
            synchronized (lock) {
                dropAnswers(key);
                receivers = List.copyOf(shown.getOrDefault(key, List.of()));
                if (!receivers.isEmpty()) held.put(key, new Held(view, entry, repository.targets()));
            }
        }
        for (Consumer<DataplexView> receiver : receivers) receiver.accept(view);
        return view;
    }

    private static @NotNull LineageCacheEntry withExpansionsOf(@NotNull LineageCacheEntry read,
                                                               @Nullable LineageCacheEntry current) {
        if (current == null) return read;
        LineageCacheEntry entry = read;
        for (Expansion expansion : current.expansions()) entry = entry.withExpansion(expansion);
        return entry;
    }

    @Override
    public @NotNull DataplexView include(@NotNull DataplexView view, @NotNull String nodeId) {
        if (view.fqnOf(nodeId) != null) return view;
        DataplexSnapshot larger = new DataplexGraphLoader(inRange(view.range()), view.scope(), clock, calls)
                .include(view.snapshot(), EntityNames.fqnOfNodeId(nodeId));
        if (larger == view.snapshot()) return view;
        DataplexView after = new DataplexView(larger, DataplexGraphBuilder.build(larger, view.repository(), view.assertionNodeIds()),
                view.range(), view.scope(), view.repository(), view.assertionNodeIds());
        keep(view, after, null);
        return after;
    }

    @Override
    public void retain(@NotNull LineageScope scope, @NotNull TimeRange range,
                       @NotNull Consumer<DataplexView> onNewEntry) {
        synchronized (lock) {
            shown.computeIfAbsent(EntryKey.of(scope, range), k -> new ArrayList<>()).add(onNewEntry);
        }
    }

    @Override
    public void release(@NotNull LineageScope scope, @NotNull TimeRange range,
                        @NotNull Consumer<DataplexView> onNewEntry) {
        EntryKey key = EntryKey.of(scope, range);
        synchronized (lock) {
            List<Consumer<DataplexView>> left = shown.get(key);
            if (left != null) left.remove(onNewEntry);
            if (left != null && !left.isEmpty()) return;
            shown.remove(key);
            held.remove(key);
            checked.remove(key);
            dropAnswers(key);
            if (shown.keySet().stream().noneMatch(other -> other.parent().equals(key.parent()))) {
                answers.keySet().removeIf(answer -> answer.startsWith("columns|" + key.parent() + '|'));
            }
        }
    }

    private void hold(@NotNull EntryKey key, @NotNull Held candidate) {
        if (!shown.containsKey(key)) return;
        Held current = held.get(key);
        if (current == null || !current.entry().snapshot().loadedAt().isAfter(candidate.entry().snapshot().loadedAt())) {
            held.put(key, candidate);
        }
    }

    private void dropAnswers(@NotNull EntryKey key) {
        String part = '|' + key.parent() + '|' + key.range() + '|';
        answers.keySet().removeIf(answer -> answer.contains(part));
    }

    private @Nullable LineageCacheEntry entryOf(@NotNull EntryKey key, @NotNull LineageScope scope, @NotNull TimeRange range) {
        synchronized (lock) {
            Held current = held.get(key);
            if (current != null) return current.entry();
        }
        return cache.get(scope, range);
    }

    private void keep(@NotNull DataplexView before, @NotNull DataplexView after, @Nullable Expansion expansion) {
        EntryKey key = EntryKey.of(before.scope(), before.range());
        synchronized (commits) {
            LineageCacheEntry entry = entryOf(key, before.scope(), before.range());
            if (entry == null) return;
            boolean expandedEntry = entry.snapshot() == before.snapshot() || entry.snapshot().equals(before.snapshot());
            LineageCacheEntry updated = expandedEntry ? entry.withSnapshot(after.snapshot()) : entry;
            if (expansion != null) updated = updated.withExpansion(expansion);
            if (updated == entry) return;
            cache.put(updated);
            synchronized (lock) {
                Held current = held.get(key);
                if (current != null && current.entry() == entry) {
                    held.put(key, new Held(expandedEntry ? after : current.view(), updated, current.targets()));
                }
            }
        }
    }

    private @NotNull Repository repositoryOf(@NotNull CompiledGraph compiled) {
        LineageScope scope = scopeOf(compiled);
        LineageGraph graph = new LineageExtractorImpl().extract(compiled);
        Set<String> assertions = assertionNodeIds(compiled);
        Set<String> roots = new LinkedHashSet<>();
        for (LineageNode node : graph.nodes()) {
            if (!assertions.contains(node.id())) roots.add(EntityNames.fqnOf(node.fullName()));
        }
        return new Repository(scope, graph, assertions, targetsOf(graph), List.copyOf(roots));
    }

    private static @NotNull DataplexView viewOf(@NotNull LineageCacheEntry entry, @NotNull Repository repository) {
        return new DataplexView(entry.snapshot(),
                DataplexGraphBuilder.build(entry.snapshot(), repository.graph(), repository.assertionNodeIds()),
                entry.range(), repository.scope(), repository.graph(), repository.assertionNodeIds());
    }

    private <T> T shared(@NotNull Map<EntryKey, CompletableFuture<T>> inFlight, @NotNull EntryKey key,
                         @NotNull Supplier<T> work) {
        CompletableFuture<T> mine = new CompletableFuture<>();
        CompletableFuture<T> running;
        synchronized (lock) {
            running = inFlight.putIfAbsent(key, mine);
        }
        if (running != null) return await(running);
        try {
            T value = work.get();
            mine.complete(value);
            return value;
        } catch (RuntimeException | Error failure) {
            mine.completeExceptionally(failure);
            throw failure;
        } finally {
            synchronized (lock) {
                inFlight.remove(key, mine);
            }
        }
    }

    private static <T> T await(@NotNull CompletableFuture<T> running) {
        try {
            return running.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException failure) throw failure;
            if (e.getCause() instanceof Error failure) throw failure;
            throw e;
        }
    }


    @Override
    public @NotNull DataplexView expand(@NotNull DataplexView view, @NotNull String nodeId, @NotNull Direction direction) {
        String fqn = view.fqnOf(nodeId);
        if (fqn == null) return view;
        DataplexSnapshot expanded = new DataplexGraphLoader(inRange(view.range()), view.scope(), clock, calls)
                .expand(view.snapshot(), fqn, direction);
        if (expanded == view.snapshot()) return view;
        DataplexView larger = new DataplexView(expanded,
                DataplexGraphBuilder.build(expanded, view.repository(), view.assertionNodeIds()),
                view.range(), view.scope(), view.repository(), view.assertionNodeIds());
        keep(view, larger, new Expansion(fqn, direction));
        return larger;
    }

    @Override
    public @NotNull Freshness freshness(@NotNull DataplexView view, @NotNull String nodeId) {
        String fqn = view.fqnOf(nodeId);
        LineageNode node = view.graph().node(nodeId);
        boolean isView = node != null && ("view".equals(node.dataformType()) || "materialized_view".equals(node.dataformType()));
        if (fqn == null) return new Freshness(null, null, isView);
        return answer("freshness", view, fqn, () -> {
            RecordedLink latest = incoming(view, fqn).stream().max(Comparator.comparing(RecordedLink::lastSeen)).orElse(null);
            if (latest == null) return new Freshness(null, null, isView);
            List<String> names = client.processNames(view.scope(), List.of(latest.name()))
                    .getOrDefault(latest.name(), List.of());
            return new Freshness(latest.lastSeen(), names.isEmpty() ? null : client.process(names.getFirst()).displayName(),
                    isView);
        });
    }

    @Override
    public @NotNull LinkDetails linkDetails(@NotNull DataplexView view, @NotNull String fromNodeId, @NotNull String toNodeId) {
        List<RecordedLink> links = between(view, fromNodeId, toNodeId);
        if (links.isEmpty()) return LinkDetails.NONE;
        return answer("link", view, fromNodeId + ">" + toNodeId, () -> {
            Instant first = links.stream().map(RecordedLink::firstSeen).min(Comparator.naturalOrder()).orElseThrow();
            Instant last = links.stream().map(RecordedLink::lastSeen).max(Comparator.naturalOrder()).orElseThrow();
            Map<String, List<String>> byLink = client.processNames(view.scope(), links.stream().map(RecordedLink::name).toList());
            Set<String> names = new LinkedHashSet<>();
            for (RecordedLink link : links) names.addAll(byLink.getOrDefault(link.name(), List.of()));
            ProcessReads read = processesAndRuns(List.copyOf(names));
            List<RecordedRun> runs = new ArrayList<>();
            read.runs().forEach(runs::addAll);
            runs.sort(Comparator.comparing(RecordedRun::start).reversed());
            return new LinkDetails(first, last, read.processes(), List.copyOf(runs.subList(0, Math.min(runs.size(), RUNS_SHOWN))));
        });
    }

    @Override
    public @NotNull List<ProcessRun> runsWriting(@NotNull DataplexView view, @NotNull String nodeId) {
        String fqn = view.fqnOf(nodeId);
        if (fqn == null) return List.of();
        return answer("runs", view, fqn, () -> processRuns(view, incoming(view, fqn)));
    }

    @Override
    public @NotNull List<ProcessRun> runsOfLink(@NotNull DataplexView view, @NotNull String fromNodeId,
                                                @NotNull String toNodeId) {
        List<RecordedLink> links = between(view, fromNodeId, toNodeId);
        if (links.isEmpty()) return List.of();
        return answer("linkRuns", view, fromNodeId + ">" + toNodeId, () -> processRuns(view, links));
    }

    @Override
    public @NotNull List<ProcessUse> processesOf(@NotNull DataplexView view, @NotNull String nodeId) {
        String fqn = view.fqnOf(nodeId);
        if (fqn == null) return List.of();
        return answer("processes", view, fqn, () -> {
            List<Callable<List<RecordedLink>>> asks = List.of(() -> incoming(view, fqn), () -> outgoing(view, fqn));
            List<RecordedLink> links = new ArrayList<>();
            for (List<RecordedLink> found : calls.all(asks)) {
                for (RecordedLink link : found) {
                    String other = link.targetFqn().equals(fqn) ? link.sourceFqn() : link.targetFqn();
                    if (view.snapshot().entities().contains(other)) links.add(link);
                }
            }
            if (links.isEmpty()) return List.<ProcessUse>of();
            Map<String, List<RecordedProcess>> byLink = processesByLink(view.scope(),
                    links.stream().map(RecordedLink::name).toList());
            Map<String, ProcessUse> uses = new LinkedHashMap<>();
            for (RecordedLink link : links) {
                boolean wrote = link.targetFqn().equals(fqn);
                for (RecordedProcess process : byLink.getOrDefault(link.name(), List.of())) {
                    uses.merge(wrote + "|" + process.displayName(),
                            new ProcessUse(process.displayName(), wrote, link.lastSeen()),
                            (known, other) -> known.lastSeen().isAfter(other.lastSeen()) ? known : other);
                }
            }
            return uses.values().stream().sorted(Comparator.comparing(ProcessUse::lastSeen).reversed()).toList();
        });
    }

    @Override
    public @NotNull List<TraceEntry> trace(@NotNull DataplexView view, @NotNull String nodeId, @NotNull Direction direction) {
        String root = view.fqnOf(nodeId);
        if (root == null) return List.of();
        return answer("trace" + direction, view, root, () -> {
            StreamAnswer answer = inRange(view.range()).stream(view.scope(), List.of(root), EnumSet.allOf(Direction.class),
                    TRACE_DEPTH, null);
            for (Direction other : Direction.values()) {
                if (other != direction) remember("trace" + other, view, root, walk(view, root, other, answer));
            }
            return walk(view, root, direction, answer);
        });
    }

    private @NotNull List<TraceEntry> walk(@NotNull DataplexView view, @NotNull String root, @NotNull Direction direction,
                                           @NotNull StreamAnswer answer) {
        Map<String, Integer> levels = new LinkedHashMap<>();
        for (StreamedLink link : answer.links()) {
            if (link.direction() != direction) continue;
            String other = link.farEnd();
            if (other.equals(root) || view.assertionNodeIds().contains(EntityNames.nodeIdOf(other))) continue;
            levels.merge(other, link.depth(), Math::min);
        }
        return levels.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .limit(DataplexGraphLoader.MAX_ENTITIES)
                .map(entry -> new TraceEntry(entry.getKey(), entry.getValue()))
                .toList();
    }

    private @NotNull List<RecordedLink> between(@NotNull DataplexView view, @NotNull String fromNodeId,
                                                @NotNull String toNodeId) {
        String fromFqn = view.fqnOf(fromNodeId);
        String toFqn = view.fqnOf(toNodeId);
        if (fromFqn == null || toFqn == null) return List.of();
        return incoming(view, toFqn).stream().filter(link -> link.sourceFqn().equals(fromFqn)).toList();
    }

    private @NotNull List<ProcessRun> processRuns(@NotNull DataplexView view, @NotNull List<RecordedLink> links) {
        if (links.isEmpty()) return List.of();
        Map<String, List<String>> byLink = client.processNames(view.scope(), links.stream().map(RecordedLink::name).toList());
        Map<String, Set<String>> sources = new LinkedHashMap<>();
        Map<String, String> targets = new LinkedHashMap<>();
        for (RecordedLink link : links) {
            for (String process : byLink.getOrDefault(link.name(), List.of())) {
                sources.computeIfAbsent(process, known -> new LinkedHashSet<>()).add(link.sourceFqn());
                targets.putIfAbsent(process, link.targetFqn());
            }
        }
        List<String> names = List.copyOf(sources.keySet());
        ProcessReads read = processesAndRuns(names);
        List<ProcessRun> runs = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            RecordedProcess process = read.processes().get(i);
            for (RecordedRun run : read.runs().get(i)) {
                runs.add(new ProcessRun(run, process, List.copyOf(sources.get(names.get(i))), targets.get(names.get(i))));
            }
        }
        runs.sort(Comparator.comparing((ProcessRun run) -> run.run().start()).reversed());
        return List.copyOf(runs.subList(0, Math.min(runs.size(), RUNS_SHOWN)));
    }

    private @NotNull List<RecordedLink> incoming(@NotNull DataplexView view, @NotNull String fqn) {
        return answer("incoming", view, fqn, () -> inRange(view.range()).links(view.scope(), fqn, Direction.UPSTREAM));
    }

    private @NotNull List<RecordedLink> outgoing(@NotNull DataplexView view, @NotNull String fqn) {
        return answer("outgoing", view, fqn, () -> inRange(view.range()).links(view.scope(), fqn, Direction.DOWNSTREAM));
    }

    private @NotNull Map<String, List<RecordedProcess>> processesByLink(@NotNull LineageScope scope,
                                                                        @NotNull List<String> linkNames) {
        Map<String, List<String>> names = client.processNames(scope, linkNames);
        List<String> distinct = names.values().stream().flatMap(List::stream).distinct().toList();
        List<Callable<RecordedProcess>> reads = new ArrayList<>();
        for (String name : distinct) reads.add(() -> client.process(name));
        List<RecordedProcess> read = calls.all(reads);
        Map<String, RecordedProcess> byName = new HashMap<>();
        for (int i = 0; i < distinct.size(); i++) byName.put(distinct.get(i), read.get(i));
        Map<String, List<RecordedProcess>> byLink = new LinkedHashMap<>();
        names.forEach((link, ofLink) -> byLink.put(link, ofLink.stream().map(byName::get).toList()));
        return byLink;
    }

    private @NotNull ProcessReads processesAndRuns(@NotNull List<String> names) {
        List<Callable<Object>> reads = new ArrayList<>();
        for (String name : names) {
            reads.add(() -> client.process(name));
            reads.add(() -> client.runs(name, RUNS_PER_PROCESS));
        }
        List<Object> read = calls.all(reads);
        List<RecordedProcess> processes = new ArrayList<>(names.size());
        List<List<RecordedRun>> runs = new ArrayList<>(names.size());
        for (int i = 0; i < names.size(); i++) {
            processes.add((RecordedProcess) read.get(2 * i));
            runs.add(runsRead(read.get(2 * i + 1)));
        }
        return new ProcessReads(processes, runs);
    }

    @SuppressWarnings("unchecked")
    private static @NotNull List<RecordedRun> runsRead(@NotNull Object read) {
        return (List<RecordedRun>) read;
    }

    @Override
    public @NotNull DataplexColumnLineage.Result columnLineage(@NotNull DataplexView view,
                                                              @NotNull List<ColumnRef> roots) {
        Set<String> loaded = new TreeSet<>();
        for (String fqn : view.snapshot().entities()) {
            String fullName = EntityNames.fullNameOf(fqn);
            if (fullName != null) loaded.add(fullName);
        }
        String key = "columns|" + view.scope().parent() + '|' + String.join(",", loaded) + '|'
                + roots.stream().map(ColumnRef::id).collect(Collectors.joining(","));
        synchronized (lock) {
            Object known = answers.get(key);
            if (known != null) return (DataplexColumnLineage.Result) known;
        }
        DataplexColumnLineage.Result result = DataplexColumnLineage.load(client, view.scope(), roots, loaded);
        if (result.reason() == null) {
            synchronized (lock) {
                answers.put(key, result);
            }
        }
        return result;
    }

    private void remember(@NotNull String kind, @NotNull DataplexView view, @NotNull String subject, @NotNull Object value) {
        String key = answerKey(kind, view, subject);
        synchronized (lock) {
            answers.putIfAbsent(key, value);
        }
    }

    private static @NotNull String answerKey(@NotNull String kind, @NotNull DataplexView view, @NotNull String subject) {
        return kind + '|' + view.scope().parent() + '|' + view.range() + '|' + subject;
    }

    @SuppressWarnings("unchecked")
    private <T> @NotNull T answer(@NotNull String kind, @NotNull DataplexView view, @NotNull String subject,
                                  @NotNull Supplier<T> ask) {
        String key = answerKey(kind, view, subject);
        synchronized (lock) {
            Object known = answers.get(key);
            if (known != null) return (T) known;
        }
        T asked = ask.get();
        synchronized (lock) {
            answers.put(key, asked);
        }
        return asked;
    }

    @Override
    public @NotNull LineageScope scopeOf(@NotNull CompiledGraph compiled) {
        DataformProjectDefaults projectDefaults = defaults.apply(compiled);
        String projectId = projectDefaults.database();
        if (projectId == null || projectId.isBlank()) {
            throw new DataplexLineageException(DataplexLineageException.Kind.OTHER,
                    "No Google Cloud project: set defaultProject in workflow_settings.yaml", null);
        }
        String location = projectDefaults.location();
        return new LineageScope(projectId, location == null || location.isBlank() ? DEFAULT_LOCATION : location);
    }

    private @NotNull DataplexLineageClient inRange(@NotNull TimeRange range) {
        Instant since = clock.instant().minus(range.length());
        return new DataplexLineageClient() {
            @Override
            public @NotNull List<RecordedLink> links(@NotNull LineageScope scope, @NotNull String entityFqn,
                                                     @NotNull Direction direction) {
                return client.links(scope, entityFqn, direction).stream()
                        .filter(link -> !link.lastSeen().isBefore(since)).toList();
            }

            @Override
            public @NotNull Map<String, List<String>> processNames(@NotNull LineageScope scope,
                                                                   @NotNull List<String> linkNames) {
                return client.processNames(scope, linkNames);
            }

            @Override
            public @NotNull RecordedProcess process(@NotNull String processName) {
                return client.process(processName);
            }

            @Override
            public @NotNull List<RecordedRun> runs(@NotNull String processName, int limit) {
                return client.runs(processName, limit);
            }

            @Override
            public @NotNull List<RecordedColumnLink> columnStream(@NotNull LineageScope scope,
                                                                  @NotNull Map<String, List<String>> rootColumns,
                                                                  @NotNull Set<Direction> directions, int maxDepth) {
                return client.columnStream(scope, rootColumns, directions, maxDepth);
            }

            @Override
            public @NotNull StreamAnswer stream(@NotNull LineageScope scope, @NotNull List<String> rootFqns,
                                                @NotNull Set<Direction> directions, int maxDepth,
                                                @Nullable Instant from) {
                return client.stream(scope, rootFqns, directions, maxDepth, from == null || from.isBefore(since) ? since : from);
            }

            @Override
            public @Nullable String newestProcess(@NotNull LineageScope scope) {
                return client.newestProcess(scope);
            }
        };
    }

    private static @NotNull Set<String> assertionNodeIds(@NotNull CompiledGraph compiled) {
        Set<String> ids = new LinkedHashSet<>();
        for (CompiledAssertion assertion : compiled.getAssertions()) {
            if (assertion.getTarget() != null) ids.add(LineageNode.idOf(assertion.getTarget().getFullName()));
        }
        return Set.copyOf(ids);
    }

    private static @NotNull String targetsOf(@NotNull LineageGraph repository) {
        Set<String> targets = new TreeSet<>();
        for (LineageNode node : repository.nodes()) targets.add(node.id() + '=' + node.dataformType());
        return targets.toString();
    }

    private record EntryKey(@NotNull String parent, @NotNull TimeRange range) {
        static @NotNull EntryKey of(@NotNull LineageScope scope, @NotNull TimeRange range) {
            return new EntryKey(scope.parent(), range);
        }
    }

    private record Checked(@Nullable String marker) {
    }

    private record ProcessReads(@NotNull List<RecordedProcess> processes, @NotNull List<List<RecordedRun>> runs) {
    }

    private record Held(@NotNull DataplexView view, @NotNull LineageCacheEntry entry, @NotNull String targets) {
    }

    private record Repository(@NotNull LineageScope scope, @NotNull LineageGraph graph, @NotNull Set<String> assertionNodeIds,
                              @NotNull String targets, @NotNull List<String> roots) {
    }

}
