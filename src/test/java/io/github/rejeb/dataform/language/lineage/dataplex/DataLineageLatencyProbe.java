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

import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.datacatalog.lineage.v1.BatchSearchLinkProcessesRequest;
import com.google.cloud.datacatalog.lineage.v1.EntityReference;
import com.google.cloud.datacatalog.lineage.v1.LineageClient;
import com.google.cloud.datacatalog.lineage.v1.LineageLink;
import com.google.cloud.datacatalog.lineage.v1.LineageSettings;
import com.google.cloud.datacatalog.lineage.v1.Link;
import com.google.cloud.datacatalog.lineage.v1.ListProcessesRequest;
import com.google.cloud.datacatalog.lineage.v1.ListRunsRequest;
import com.google.cloud.datacatalog.lineage.v1.Process;
import com.google.cloud.datacatalog.lineage.v1.ProcessLinkInfo;
import com.google.cloud.datacatalog.lineage.v1.ProcessLinks;
import com.google.cloud.datacatalog.lineage.v1.Run;
import com.google.cloud.datacatalog.lineage.v1.SearchLineageStreamingRequest;
import com.google.cloud.datacatalog.lineage.v1.SearchLineageStreamingResponse;
import com.google.cloud.datacatalog.lineage.v1.SearchLinksRequest;
import com.google.gson.Gson;
import io.github.rejeb.dataform.language.columns.model.ColumnRef;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.lineage.dataplex.DataplexLineageClient.Direction;
import io.github.rejeb.dataform.language.lineage.dataplex.cache.DataplexLineageCache;
import io.github.rejeb.dataform.language.lineage.dataplex.cache.DataplexLineageCacheImpl;
import io.github.rejeb.dataform.language.lineage.dataplex.cache.LineageCacheEntry;
import io.github.rejeb.dataform.language.lineage.graph.LineageNode;
import io.github.rejeb.dataform.language.service.DataformProjectDefaults;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import javax.net.ssl.SSLContext;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Opt-in, read-only probe of how long the Knowledge Catalog reads behind the lineage views take, through
 * {@link DataplexLineageServiceImpl} as the plugin makes them, over a client that counts its calls. It never
 * runs in the normal test suite: it needs {@code DATAPLEX_LATENCY_PROJECT}, {@code DATAPLEX_LATENCY_LOCATION},
 * {@code DATAPLEX_LATENCY_TABLES} (comma-separated {@code project.dataset.table}, the repository tables),
 * {@code DATAPLEX_LATENCY_COLUMN} ({@code project.dataset.table#column}), {@code DATAPLEX_LATENCY_TOKEN} and
 * {@code DATAPLEX_LATENCY_OUT}.
 */
@EnabledIfEnvironmentVariable(named = "DATAPLEX_LATENCY_TOKEN", matches = ".+")
class DataLineageLatencyProbe {

    private static final int REPEATS = 3;

    private PrintStream out;
    private LineageScope scope;
    private LineageClient client;
    private final Map<String, AtomicInteger> counts = new LinkedHashMap<>();

    @Test
    void timesTheReadsBehindTheLineageViews() throws Exception {
        String project = System.getenv("DATAPLEX_LATENCY_PROJECT");
        String location = System.getenv("DATAPLEX_LATENCY_LOCATION");
        scope = new LineageScope(project, location);
        GoogleCredentials credentials = GoogleCredentials.create(new AccessToken(System.getenv("DATAPLEX_LATENCY_TOKEN"), null))
                .createWithQuotaProject(project);
        List<String> tables = Arrays.stream(System.getenv("DATAPLEX_LATENCY_TABLES").split(","))
                .map(String::trim).filter(name -> !name.isEmpty()).toList();
        String[] column = System.getenv("DATAPLEX_LATENCY_COLUMN").split("#");
        ColumnRef root = new ColumnRef(column[0], column[1]);
        String nodeId = LineageNode.idOf(root.tableFullName());
        CompiledGraph compiled = compiled(tables);
        DataformProjectDefaults defaults = new DataformProjectDefaults(project, null, location, null);
        Supplier<DataplexLineageServiceImpl> fresh = () -> new DataplexLineageServiceImpl(new ProbeClient(), Clock.systemUTC(),
                graph -> defaults, ParallelCalls.shared());
        try (PrintStream report = new PrintStream(Files.newOutputStream(Path.of(System.getenv("DATAPLEX_LATENCY_OUT"))),
                true, StandardCharsets.UTF_8); LineageClient opened = newClient(credentials)) {
            out = report;
            client = opened;
            fresh.get().reload(compiled, TimeRange.LAST_30_DAYS);
            DataplexView view = fresh.get().reload(compiled, TimeRange.LAST_30_DAYS);
            out.println("repository tables=" + tables.size() + " loaded entities=" + view.snapshot().entities().size());

            time("full lineage (reload)", () -> fresh.get().reload(compiled, TimeRange.LAST_30_DAYS));
            time("stale cache: check, then reload", () -> {
                DataplexLineageServiceImpl service = new DataplexLineageServiceImpl(new ProbeClient(), Clock.systemUTC(),
                        graph -> defaults, new StaleCache(), ParallelCalls.shared());
                service.reload(compiled, TimeRange.LAST_30_DAYS);
                counts.clear();
                long start = System.nanoTime();
                boolean stale = service.needsReload(compiled, TimeRange.LAST_30_DAYS);
                service.reload(compiled, TimeRange.LAST_30_DAYS);
                return "stale=" + stale + " check+reload ms=" + (System.nanoTime() - start) / 1_000_000;
            });
            time("runs writing " + root.tableFullName(), () -> fresh.get().runsWriting(view, nodeId));
            for (Direction direction : Direction.values()) {
                time("trace " + direction, () -> fresh.get().trace(view, nodeId, direction));
            }
            time("trace both directions, one service", () -> {
                DataplexLineageServiceImpl service = fresh.get();
                for (Direction direction : Direction.values()) service.trace(view, nodeId, direction);
                return null;
            });
            time("column lineage of " + root.id(), () -> {
                DataplexColumnLineage.Result result = fresh.get().columnLineage(view, List.of(root));
                return "columns=" + result.graph().columns().size() + " links=" + result.graph().edges().size()
                        + (result.reason() == null ? "" : " reason=" + result.reason());
            });
        }
    }

    private void time(@NotNull String what, @NotNull Supplier<Object> read) {
        out.println();
        out.println("== " + what);
        for (int i = 0; i < REPEATS; i++) {
            counts.clear();
            long start = System.nanoTime();
            Object answer = read.get();
            long took = (System.nanoTime() - start) / 1_000_000;
            out.println("  ms=" + took + " calls=" + counts + (answer instanceof String text ? " " + text : ""));
        }
    }

    private void count(@NotNull String call) {
        synchronized (counts) {
            counts.computeIfAbsent(call, k -> new AtomicInteger()).incrementAndGet();
        }
    }

    private static @NotNull CompiledGraph compiled(@NotNull List<String> tables) {
        List<String> json = new ArrayList<>();
        for (String table : tables) {
            String[] parts = table.split("\\.");
            json.add("{\"target\":{\"database\":\"" + parts[0] + "\",\"schema\":\"" + parts[1] + "\",\"name\":\"" + parts[2]
                    + "\"},\"enumType\":\"table\",\"fileName\":\"definitions/" + parts[2] + ".sqlx\"}");
        }
        return new Gson().fromJson("{\"tables\":[" + String.join(",", json) + "]}", CompiledGraph.class);
    }

    private static @NotNull LineageClient newClient(@NotNull GoogleCredentials credentials) throws Exception {
        NetHttpTransport transport = new NetHttpTransport.Builder()
                .setSslSocketFactory(SSLContext.getDefault().getSocketFactory()).build();
        return LineageClient.create(LineageSettings.newHttpJsonBuilder()
                .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
                .setTransportChannelProvider(LineageSettings.defaultHttpJsonTransportProviderBuilder()
                        .setHttpTransport(transport).build())
                .build());
    }

    private @NotNull List<LineageLink> read(@NotNull SearchLineageStreamingRequest request) {
        count("SearchLineageStreaming");
        List<LineageLink> links = new ArrayList<>();
        for (SearchLineageStreamingResponse response : client.searchLineageStreamingCallable().call(request)) {
            links.addAll(response.getLinksList());
        }
        return links;
    }

    private static final class StaleCache implements DataplexLineageCache {
        private final DataplexLineageCacheImpl delegate = new DataplexLineageCacheImpl();

        @Override
        public @Nullable LineageCacheEntry get(@NotNull LineageScope scope, @NotNull TimeRange range) {
            LineageCacheEntry entry = delegate.get(scope, range);
            return entry == null ? null : new LineageCacheEntry(entry.scope(), entry.range(), entry.snapshot(),
                    entry.targets(), "processes/stale", entry.expansions());
        }

        @Override
        public void put(@NotNull LineageCacheEntry entry) {
            delegate.put(entry);
        }

        @Override
        public void remove(@NotNull LineageScope scope, @NotNull TimeRange range) {
            delegate.remove(scope, range);
        }
    }

    private final class ProbeClient implements DataplexLineageClient {

        @Override
        public @NotNull List<RecordedLink> links(@NotNull LineageScope scope, @NotNull String entityFqn,
                                                 @NotNull Direction direction) {
            count("SearchLinks");
            SearchLinksRequest.Builder request = SearchLinksRequest.newBuilder().setParent(scope.parent()).setPageSize(100);
            EntityReference entity = EntityReference.newBuilder().setFullyQualifiedName(entityFqn).build();
            if (direction == Direction.UPSTREAM) request.setTarget(entity);
            else request.setSource(entity);
            List<RecordedLink> links = new ArrayList<>();
            for (Link link : client.searchLinks(request.build()).iterateAll()) {
                links.add(DataplexLineageClientImpl.toRecordedLink(link));
            }
            return links;
        }

        @Override
        public @NotNull Map<String, List<String>> processNames(@NotNull LineageScope scope,
                                                               @NotNull List<String> linkNames) {
            if (linkNames.isEmpty()) return Map.of();
            count("BatchSearchLinkProcesses");
            Map<String, List<String>> byLink = new LinkedHashMap<>();
            for (ProcessLinks processLinks : client.batchSearchLinkProcesses(BatchSearchLinkProcessesRequest.newBuilder()
                    .setParent(scope.parent()).addAllLinks(linkNames).setPageSize(100).build()).iterateAll()) {
                for (ProcessLinkInfo info : processLinks.getLinksList()) {
                    List<String> ofLink = byLink.computeIfAbsent(info.getLink(), k -> new ArrayList<>());
                    if (!ofLink.contains(processLinks.getProcess())) ofLink.add(processLinks.getProcess());
                }
            }
            return byLink;
        }

        @Override
        public @NotNull RecordedProcess process(@NotNull String processName) {
            count("GetProcess");
            return DataplexLineageClientImpl.toRecordedProcess(client.getProcess(processName));
        }

        @Override
        public @NotNull List<RecordedRun> runs(@NotNull String processName, int limit) {
            count("ListRuns");
            List<RecordedRun> runs = new ArrayList<>();
            for (Run run : client.listRuns(ListRunsRequest.newBuilder().setParent(processName)
                    .setPageSize(Math.min(limit, 100)).build()).iterateAll()) {
                runs.add(DataplexLineageClientImpl.toRecordedRun(run));
                if (runs.size() == limit) break;
            }
            return runs;
        }

        @Override
        public @NotNull List<RecordedColumnLink> columnStream(@NotNull LineageScope scope,
                                                              @NotNull Map<String, List<String>> rootColumns,
                                                              @NotNull Set<Direction> directions, int maxDepth) {
            return DataplexLineageClientImpl.columnStream(DataLineageLatencyProbe.this::read, ParallelCalls.shared(), scope,
                    rootColumns, directions, maxDepth);
        }

        @Override
        public @NotNull StreamAnswer stream(@NotNull LineageScope scope, @NotNull List<String> rootFqns,
                                            @NotNull Set<Direction> directions, int maxDepth, @Nullable Instant since) {
            return DataplexLineageClientImpl.stream(DataLineageLatencyProbe.this::read, ParallelCalls.shared(), scope,
                    rootFqns, directions, maxDepth, since);
        }

        @Override
        public @Nullable String newestProcess(@NotNull LineageScope scope) {
            count("ListProcesses");
            for (Process process : client.listProcesses(ListProcessesRequest.newBuilder().setParent(scope.parent())
                    .setPageSize(1).build()).getPage().getValues()) {
                return process.getName();
            }
            return null;
        }
    }
}
