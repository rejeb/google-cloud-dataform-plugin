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
package io.github.rejeb.dataform.language.lineage.view;

import io.github.rejeb.dataform.language.lineage.dataplex.DataplexGraphBuilder;
import io.github.rejeb.dataform.language.lineage.dataplex.DataplexLineageClient;
import io.github.rejeb.dataform.language.lineage.dataplex.DataplexLineageException;
import io.github.rejeb.dataform.language.lineage.dataplex.DataplexLineageService;
import io.github.rejeb.dataform.language.lineage.dataplex.DataplexSnapshot;
import io.github.rejeb.dataform.language.lineage.dataplex.DataplexView;
import io.github.rejeb.dataform.language.lineage.dataplex.EntityNames;
import io.github.rejeb.dataform.language.lineage.dataplex.Freshness;
import io.github.rejeb.dataform.language.lineage.dataplex.LineageScope;
import io.github.rejeb.dataform.language.lineage.dataplex.LinkDetails;
import io.github.rejeb.dataform.language.lineage.dataplex.ProcessRun;
import io.github.rejeb.dataform.language.lineage.dataplex.ProcessUse;
import io.github.rejeb.dataform.language.lineage.dataplex.RecordedLink;
import io.github.rejeb.dataform.language.lineage.dataplex.TimeRange;
import io.github.rejeb.dataform.language.lineage.dataplex.TraceEntry;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.lineage.graph.LineageGraph;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * Recorded lineage views, a scripted lineage service and a manual executor for the view tests.
 */
public final class DataplexViewFixtures {

    public static final Instant LOADED_AT = Instant.parse("2026-11-12T10:00:00Z");
    public static final LineageScope SCOPE = new LineageScope("p", "EU");

    private DataplexViewFixtures() {
    }

    public static String fqn(String name) {
        return name.contains(":") ? name : EntityNames.fqnOf(name);
    }

    /**
     * A view rooted at the given table, with one link per pair of names (source, target). A name
     * without a scheme is the full name of a BigQuery table.
     */
    public static DataplexView view(LineageGraph repository, String root, String... ends) {
        return view(repository, false, root, ends);
    }

    public static DataplexView view(LineageGraph repository, boolean truncated, String root, String... ends) {
        return view(repository, truncated, root, List.of(ends), List.of(), Set.of());
    }

    /**
     * A view with links known but not loaded (pairs of names, as the loaded ones) and entities whose
     * upstream side was not asked for.
     */
    public static DataplexView view(LineageGraph repository, boolean truncated, String root, List<String> ends,
                             List<String> pendingEnds, Set<String> unqueriedUpstream) {
        Set<String> entities = new LinkedHashSet<>();
        entities.add(fqn(root));
        List<RecordedLink> links = new ArrayList<>();
        for (int i = 0; i < ends.size(); i += 2) {
            entities.add(fqn(ends.get(i)));
            entities.add(fqn(ends.get(i + 1)));
            links.add(link("links/" + i, ends.get(i), ends.get(i + 1)));
        }
        List<RecordedLink> pending = new ArrayList<>();
        for (int i = 0; i < pendingEnds.size(); i += 2) {
            pending.add(link("pending/" + i, pendingEnds.get(i), pendingEnds.get(i + 1)));
        }
        Set<String> unqueried = new LinkedHashSet<>();
        unqueriedUpstream.forEach(name -> unqueried.add(fqn(name)));
        Set<String> read = new LinkedHashSet<>(entities);
        read.removeAll(unqueried);
        DataplexSnapshot snapshot = new DataplexSnapshot(Set.of(fqn(root)), entities, links, pending, unqueried, Set.of(),
                Set.of(), read, truncated, LOADED_AT);
        return new DataplexView(snapshot, DataplexGraphBuilder.build(snapshot, repository, Set.of()),
                TimeRange.LAST_30_DAYS, SCOPE, repository, Set.of());
    }

    private static RecordedLink link(String name, String source, String target) {
        return new RecordedLink(name, fqn(source), fqn(target), LOADED_AT.minusSeconds(86_400), LOADED_AT.minusSeconds(3_600));
    }

    /** A lineage service that answers each load with the next scripted answer. */
    public static final class ScriptedService implements DataplexLineageService {

        public final List<TimeRange> ranges = new ArrayList<>();
        public final List<String> expands = new ArrayList<>();
        private final Deque<Supplier<DataplexView>> answers = new ArrayDeque<>();
        public Freshness freshness = new Freshness(null, null, false);
        public RuntimeException freshnessFailure;
        public LinkDetails linkDetails = LinkDetails.NONE;
        public List<ProcessRun> runs = List.of();
        public final Map<String, List<ProcessRun>> runsOf = new HashMap<>();
        public final List<String> runsAsked = new ArrayList<>();
        public RuntimeException runsFailure;
        public List<TraceEntry> trace = List.of();
        public final List<String> traces = new ArrayList<>();
        public RuntimeException traceFailure;
        public List<ProcessUse> processUses = List.of();
        public final List<String> linkDetailsAsked = new ArrayList<>();
        public DataplexView expanded;
        public io.github.rejeb.dataform.language.lineage.dataplex.DataplexColumnLineage.Result columnLineage =
                new io.github.rejeb.dataform.language.lineage.dataplex.DataplexColumnLineage.Result(
                        io.github.rejeb.dataform.language.columns.analysis.ColumnLineageGraph.builder().build(), null);
        public final List<List<String>> columnRootsAsked = new ArrayList<>();
        public final List<String> log = java.util.Collections.synchronizedList(new ArrayList<>());
        public DataplexView cached;
        public boolean stale;
        public RuntimeException checkFailure;
        public DataplexView included;
        public final List<java.util.function.Consumer<DataplexView>> receivers =
                java.util.Collections.synchronizedList(new ArrayList<>());
        public java.util.function.Consumer<DataplexView> lastReceiver;

        /**
         * Gives a new entry to every view holding it, as a reload asked by another view does.
         */
        public void announce(DataplexView view) {
            List<java.util.function.Consumer<DataplexView>> holding;
            synchronized (receivers) {
                holding = List.copyOf(receivers);
            }
            holding.forEach(receiver -> receiver.accept(view));
        }

        public ScriptedService answer(DataplexView view) {
            answers.add(() -> view);
            return this;
        }

        public ScriptedService answerWith(Supplier<DataplexView> answer) {
            answers.add(answer);
            return this;
        }

        public ScriptedService fail(DataplexLineageException.Kind kind) {
            answers.add(() -> {
                throw new DataplexLineageException(kind, "failed: " + kind, null);
            });
            return this;
        }

        @Override
        public @NotNull LineageScope scopeOf(@NotNull CompiledGraph compiled) {
            return SCOPE;
        }

        @Override
        public @Nullable DataplexView cachedView(@NotNull CompiledGraph compiled, @NotNull TimeRange range) {
            log.add("CACHED " + range);
            return cached;
        }

        @Override
        public boolean needsReload(@NotNull CompiledGraph compiled, @NotNull TimeRange range) {
            log.add("CHECK " + range);
            if (checkFailure != null) throw checkFailure;
            return stale;
        }

        @Override
        public @NotNull DataplexView reload(@NotNull CompiledGraph compiled, @NotNull TimeRange range) {
            log.add("RELOAD " + range);
            ranges.add(range);
            Supplier<DataplexView> answer = answers.size() > 1 ? answers.poll() : answers.peek();
            if (answer == null) throw new IllegalStateException("no scripted answer");
            DataplexView view = answer.get();
            cached = view;
            announce(view);
            return view;
        }

        @Override
        public @NotNull DataplexView include(@NotNull DataplexView view, @NotNull String nodeId) {
            log.add("INCLUDE " + nodeId);
            return included != null ? included : view;
        }

        @Override
        public void retain(@NotNull LineageScope scope, @NotNull TimeRange range,
                           @NotNull java.util.function.Consumer<DataplexView> onNewEntry) {
            log.add("RETAIN " + range);
            receivers.add(onNewEntry);
            lastReceiver = onNewEntry;
        }

        @Override
        public void release(@NotNull LineageScope scope, @NotNull TimeRange range,
                            @NotNull java.util.function.Consumer<DataplexView> onNewEntry) {
            log.add("RELEASE " + range);
            receivers.remove(onNewEntry);
        }


        @Override
        public @NotNull io.github.rejeb.dataform.language.lineage.dataplex.DataplexColumnLineage.Result columnLineage(
                @NotNull DataplexView view, @NotNull List<io.github.rejeb.dataform.language.columns.model.ColumnRef> roots) {
            columnRootsAsked.add(roots.stream().map(io.github.rejeb.dataform.language.columns.model.ColumnRef::id).toList());
            if (columnLineage == null) throw new IllegalStateException("column lineage failed");
            return columnLineage;
        }

        @Override
        public @NotNull DataplexView expand(@NotNull DataplexView view, @NotNull String nodeId,
                                            @NotNull DataplexLineageClient.Direction direction) {
            expands.add(direction + " " + nodeId);
            return expanded != null ? expanded : view;
        }

        @Override
        public @NotNull Freshness freshness(@NotNull DataplexView view, @NotNull String nodeId) {
            if (freshnessFailure != null) throw freshnessFailure;
            return freshness;
        }

        @Override
        public @NotNull LinkDetails linkDetails(@NotNull DataplexView view, @NotNull String fromNodeId,
                                                @NotNull String toNodeId) {
            linkDetailsAsked.add(fromNodeId + ">" + toNodeId);
            return linkDetails;
        }

        @Override
        public @NotNull List<ProcessUse> processesOf(@NotNull DataplexView view, @NotNull String nodeId) {
            return processUses;
        }

        @Override
        public @NotNull List<ProcessRun> runsWriting(@NotNull DataplexView view, @NotNull String nodeId) {
            return runsFor(nodeId);
        }

        @Override
        public @NotNull List<ProcessRun> runsOfLink(@NotNull DataplexView view, @NotNull String fromNodeId,
                                                    @NotNull String toNodeId) {
            return runsFor(fromNodeId + ">" + toNodeId);
        }

        @Override
        public @NotNull List<TraceEntry> trace(@NotNull DataplexView view, @NotNull String nodeId,
                                               @NotNull DataplexLineageClient.Direction direction) {
            traces.add(direction + " " + nodeId);
            if (traceFailure != null) throw traceFailure;
            return trace;
        }

        private @NotNull List<ProcessRun> runsFor(@NotNull String subject) {
            runsAsked.add(subject);
            if (runsFailure != null) throw runsFailure;
            return runsOf.getOrDefault(subject, runs);
        }
    }

    /** An executor that keeps the tasks until the test runs them, in the order it chooses. */
    public static final class ManualExecutor implements Executor {

        public final List<Runnable> tasks = new ArrayList<>();

        @Override
        public void execute(@NotNull Runnable task) {
            tasks.add(task);
        }

        public void run(int index) {
            tasks.get(index).run();
        }

        public void runAll() {
            List<Runnable> pending = new ArrayList<>(tasks);
            tasks.clear();
            pending.forEach(Runnable::run);
        }
    }
}
