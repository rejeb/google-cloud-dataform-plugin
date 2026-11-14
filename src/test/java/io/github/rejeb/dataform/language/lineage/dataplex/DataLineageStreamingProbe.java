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

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.datacatalog.lineage.v1.EntityReference;
import com.google.cloud.datacatalog.lineage.v1.LineageClient;
import com.google.cloud.datacatalog.lineage.v1.LineageLink;
import com.google.cloud.datacatalog.lineage.v1.LineageSettings;
import com.google.cloud.datacatalog.lineage.v1.Link;
import com.google.cloud.datacatalog.lineage.v1.ListProcessesRequest;
import com.google.cloud.datacatalog.lineage.v1.ListRunsRequest;
import com.google.cloud.datacatalog.lineage.v1.MultipleEntityReference;
import com.google.cloud.datacatalog.lineage.v1.Process;
import com.google.cloud.datacatalog.lineage.v1.Run;
import com.google.cloud.datacatalog.lineage.v1.SearchLineageStreamingRequest;
import com.google.cloud.datacatalog.lineage.v1.SearchLineageStreamingRequest.RootCriteria;
import com.google.cloud.datacatalog.lineage.v1.SearchLineageStreamingRequest.SearchDirection;
import com.google.cloud.datacatalog.lineage.v1.SearchLineageStreamingRequest.SearchFilters;
import com.google.cloud.datacatalog.lineage.v1.SearchLineageStreamingRequest.SearchLimits;
import com.google.cloud.datacatalog.lineage.v1.SearchLineageStreamingResponse;
import com.google.cloud.datacatalog.lineage.v1.SearchLinksRequest;
import com.google.protobuf.Timestamp;
import com.google.type.Interval;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Opt-in, read-only probe of {@code SearchLineageStreaming} against {@code SearchLinks} on a real
 * project. It never runs in the normal test suite: it needs {@code DATAPLEX_PROBE_PROJECT},
 * {@code DATAPLEX_PROBE_LOCATION}, {@code DATAPLEX_PROBE_TABLES} (comma-separated
 * {@code project.dataset.table}) and {@code DATAPLEX_PROBE_OUT} (the report file), optionally
 * {@code DATAPLEX_PROBE_TOKEN} (an OAuth access token used instead of the application default
 * credentials).
 */
@EnabledIfEnvironmentVariable(named = "DATAPLEX_PROBE_TABLES", matches = ".+")
class DataLineageStreamingProbe {

    private static final int ROOTS_PER_CALL = 20;

    private PrintStream out;
    private int calls;

    @Test
    void comparesStreamingSearchWithSearchLinks() throws Exception {
        String project = System.getenv("DATAPLEX_PROBE_PROJECT");
        String location = System.getenv("DATAPLEX_PROBE_LOCATION");
        List<String> roots = Arrays.stream(System.getenv("DATAPLEX_PROBE_TABLES").split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).map(s -> "bigquery:" + s).toList();
        String parent = "projects/" + project + "/locations/" + location;
        try (PrintStream report = new PrintStream(Files.newOutputStream(Path.of(System.getenv("DATAPLEX_PROBE_OUT"))),
                true, StandardCharsets.UTF_8);
             LineageClient client = LineageClient.create(settings(project))) {
            out = report;
            out.println("parent=" + parent + " roots=" + roots.size());

            Map<String, Link> upstream = baseline(client, parent, roots, true);
            Map<String, Link> downstream = baseline(client, parent, roots, false);

            section("streaming depth 1 vs SearchLinks");
            compare("UPSTREAM", upstream.keySet(), streamPairs(client, parent, location, roots, SearchDirection.UPSTREAM, 1, 0, null));
            compare("DOWNSTREAM", downstream.keySet(), streamPairs(client, parent, location, roots, SearchDirection.DOWNSTREAM, 1, 0, null));

            section("streaming default depth (5)");
            for (SearchDirection direction : List.of(SearchDirection.UPSTREAM, SearchDirection.DOWNSTREAM)) {
                List<LineageLink> links = stream(client, parent, location, roots, direction, 5, 0, null);
                Map<Integer, Integer> byDepth = new TreeMap<>();
                Set<String> entities = new TreeSet<>();
                for (LineageLink link : links) {
                    byDepth.merge(link.getDepth(), 1, Integer::sum);
                    entities.add(link.getSource().getFullyQualifiedName());
                    entities.add(link.getTarget().getFullyQualifiedName());
                }
                out.println(direction + ": links=" + links.size() + " byDepth=" + byDepth + " entities=" + entities.size());
            }

            section("R7: depth from the nearest root (max_depth 3, " + ROOTS_PER_CALL + " roots per call)");
            boolean upstreamNearest = depthFromNearestRoot(client, parent, location, roots, SearchDirection.UPSTREAM, upstream.keySet());
            boolean downstreamNearest = depthFromNearestRoot(client, parent, location, roots, SearchDirection.DOWNSTREAM, downstream.keySet());
            out.println("R7 " + (upstreamNearest && downstreamNearest
                    ? "CONFIRMED: depth counts from the nearest root of the call"
                    : "NOT CONFIRMED: see the lines above"));

            section("processes on streamed links (max_process_per_link=3)");
            List<LineageLink> withProcesses = stream(client, parent, location, roots, SearchDirection.UPSTREAM, 1, 3, null);
            withProcesses.stream().limit(5).forEach(link -> out.println("  " + pair(link) + " processes="
                    + link.getProcessesList().stream().map(p -> p.getProcess().getName() + " [" + p.getProcess().getDisplayName()
                    + "] " + p.getProcess().getAttributesMap()).toList()));

            section("time_range");
            timeRange(client, parent, location, roots, upstream);

            section("processes and runs");
            processesAndRuns(client, parent);

            out.println();
            out.println("total API calls=" + calls);
        }
    }

    private Map<String, Link> baseline(LineageClient client, String parent, List<String> roots, boolean upstream) {
        section("SearchLinks " + (upstream ? "UPSTREAM" : "DOWNSTREAM") + ", one call per table");
        Map<String, Link> pairs = new LinkedHashMap<>();
        List<Long> millis = new ArrayList<>();
        for (String root : roots) {
            SearchLinksRequest.Builder request = SearchLinksRequest.newBuilder().setParent(parent).setPageSize(100);
            EntityReference entity = EntityReference.newBuilder().setFullyQualifiedName(root).build();
            if (upstream) request.setTarget(entity);
            else request.setSource(entity);
            long start = System.nanoTime();
            for (Link link : client.searchLinks(request.build()).iterateAll()) {
                pairs.putIfAbsent(link.getSource().getFullyQualifiedName() + " -> " + link.getTarget().getFullyQualifiedName(), link);
            }
            calls++;
            millis.add((System.nanoTime() - start) / 1_000_000);
        }
        out.println("calls=" + roots.size() + " links=" + pairs.size() + " msPerCall avg="
                + millis.stream().mapToLong(Long::longValue).average().orElse(0) + " max="
                + millis.stream().mapToLong(Long::longValue).max().orElse(0) + " total="
                + millis.stream().mapToLong(Long::longValue).sum());
        pairs.forEach((key, link) -> out.println("  " + key + " | " + Instant.ofEpochSecond(link.getStartTime().getSeconds())
                + " .. " + Instant.ofEpochSecond(link.getEndTime().getSeconds())));
        return pairs;
    }

    private Set<String> streamPairs(LineageClient client, String parent, String location, List<String> roots,
                                    SearchDirection direction, int depth, int processes, Interval range) {
        Set<String> pairs = new LinkedHashSet<>();
        for (LineageLink link : stream(client, parent, location, roots, direction, depth, processes, range)) {
            pairs.add(pair(link));
        }
        return pairs;
    }

    private List<LineageLink> stream(LineageClient client, String parent, String location, List<String> roots,
                                     SearchDirection direction, int depth, int processes, Interval range) {
        List<LineageLink> links = new ArrayList<>();
        for (int from = 0; from < roots.size(); from += ROOTS_PER_CALL) {
            MultipleEntityReference.Builder entities = MultipleEntityReference.newBuilder();
            for (String root : roots.subList(from, Math.min(from + ROOTS_PER_CALL, roots.size()))) {
                entities.addEntities(EntityReference.newBuilder().setFullyQualifiedName(root));
            }
            SearchLineageStreamingRequest.Builder request = SearchLineageStreamingRequest.newBuilder()
                    .setParent(parent).addLocations(location)
                    .setRootCriteria(RootCriteria.newBuilder().setEntities(entities))
                    .setDirection(direction)
                    .setLimits(SearchLimits.newBuilder().setMaxDepth(depth).setMaxResults(10_000).setMaxProcessPerLink(processes));
            if (range != null) request.setFilters(SearchFilters.newBuilder().setTimeRange(range));
            long start = System.nanoTime();
            int responses = 0;
            List<String> unreachable = new ArrayList<>();
            for (SearchLineageStreamingResponse response : client.searchLineageStreamingCallable().call(request.build())) {
                responses++;
                links.addAll(response.getLinksList());
                unreachable.addAll(response.getUnreachableList());
            }
            calls++;
            out.println("  stream " + direction + " depth=" + depth + " roots=" + Math.min(ROOTS_PER_CALL, roots.size() - from)
                    + (range == null ? "" : " range=" + describe(range)) + " -> responses=" + responses
                    + " ms=" + (System.nanoTime() - start) / 1_000_000 + (unreachable.isEmpty() ? "" : " unreachable=" + unreachable));
        }
        long withFields = links.stream().filter(l -> l.getSource().getFieldCount() > 0 || l.getTarget().getFieldCount() > 0).count();
        if (withFields > 0) out.println("  links with fields=" + withFields);
        return links;
    }

    private boolean depthFromNearestRoot(LineageClient client, String parent, String location, List<String> roots,
                                         SearchDirection direction, Set<String> searchLinksPairs) {
        boolean upstream = direction == SearchDirection.UPSTREAM;
        Map<Integer, Integer> byDepth = new TreeMap<>();
        Map<Integer, Integer> rootToRoot = new TreeMap<>();
        Map<Integer, Integer> rootReachedFurther = new TreeMap<>();
        List<String> mismatches = new ArrayList<>();
        Set<String> depthOne = new LinkedHashSet<>();
        int total = 0;
        for (int from = 0; from < roots.size(); from += ROOTS_PER_CALL) {
            List<String> batch = roots.subList(from, Math.min(from + ROOTS_PER_CALL, roots.size()));
            List<LineageLink> links = stream(client, parent, location, batch, direction, 3, 0, null);
            Map<String, Integer> distance = distances(batch, links, upstream);
            Set<String> farEnds = new LinkedHashSet<>();
            for (LineageLink link : links) {
                if (!near(link, upstream).equals(far(link, upstream))) farEnds.add(far(link, upstream));
            }
            for (LineageLink link : links) {
                total++;
                String near = near(link, upstream);
                String far = far(link, upstream);
                byDepth.merge(link.getDepth(), 1, Integer::sum);
                if (link.getDepth() == 1) depthOne.add(pair(link));
                if (near.equals(far)) continue;
                if (batch.contains(near) && batch.contains(far)) rootToRoot.merge(link.getDepth(), 1, Integer::sum);
                if (batch.contains(near) && farEnds.contains(near)) rootReachedFurther.merge(link.getDepth(), 1, Integer::sum);
                Integer expected = distance.containsKey(near) ? distance.get(near) + 1 : null;
                if (expected == null || expected != link.getDepth()) {
                    mismatches.add(pair(link) + " depth=" + link.getDepth() + " nearest=" + expected);
                }
            }
        }
        out.println(direction + ": links=" + total + " byDepth=" + byDepth);
        out.println("  links between two roots of the call, by depth=" + rootToRoot);
        out.println("  links from a root that another root also reaches, by depth=" + rootReachedFurther);
        out.println("  links whose depth is not the distance from the nearest root + 1: " + mismatches.size()
                + (mismatches.isEmpty() ? "" : " first=" + mismatches.subList(0, Math.min(5, mismatches.size()))));
        compare("  depth 1 of max_depth 3 vs SearchLinks " + direction, searchLinksPairs, depthOne);
        boolean onlyDepthOneFromRoots = rootToRoot.keySet().stream().allMatch(depth -> depth == 1)
                && rootReachedFurther.keySet().stream().allMatch(depth -> depth == 1);
        Set<String> missing = new TreeSet<>(searchLinksPairs);
        missing.removeAll(depthOne);
        return onlyDepthOneFromRoots && mismatches.isEmpty() && missing.isEmpty();
    }

    private static Map<String, Integer> distances(List<String> roots, List<LineageLink> links, boolean upstream) {
        Map<String, Integer> distance = new LinkedHashMap<>();
        roots.forEach(root -> distance.put(root, 0));
        List<String> level = new ArrayList<>(roots);
        while (!level.isEmpty()) {
            List<String> next = new ArrayList<>();
            for (LineageLink link : links) {
                String near = near(link, upstream);
                String far = far(link, upstream);
                if (level.contains(near) && !distance.containsKey(far)) {
                    distance.put(far, distance.get(near) + 1);
                    next.add(far);
                }
            }
            level = next;
        }
        return distance;
    }

    private static String near(LineageLink link, boolean upstream) {
        return (upstream ? link.getTarget() : link.getSource()).getFullyQualifiedName();
    }

    private static String far(LineageLink link, boolean upstream) {
        return (upstream ? link.getSource() : link.getTarget()).getFullyQualifiedName();
    }

    private void compare(String direction, Set<String> expected, Set<String> streamed) {
        Set<String> missing = new TreeSet<>(expected);
        missing.removeAll(streamed);
        Set<String> extra = new TreeSet<>(streamed);
        extra.removeAll(expected);
        out.println(direction + ": SearchLinks=" + expected.size() + " streamed=" + streamed.size()
                + " missing=" + missing + " extra=" + extra);
    }

    private void timeRange(LineageClient client, String parent, String location, List<String> roots, Map<String, Link> upstream) {
        TreeSet<Long> ends = new TreeSet<>();
        TreeSet<Long> starts = new TreeSet<>();
        for (Link link : upstream.values()) {
            ends.add(link.getEndTime().getSeconds());
            starts.add(link.getStartTime().getSeconds());
        }
        Set<Long> thresholds = new TreeSet<>();
        thresholds.add(Instant.now().minus(Duration.ofDays(1)).getEpochSecond());
        thresholds.add(Instant.now().minus(Duration.ofDays(7)).getEpochSecond());
        thresholds.add(midpoint(ends));
        thresholds.add(midpoint(starts));
        thresholds.remove(Long.MIN_VALUE);
        for (long threshold : thresholds) {
            Timestamp at = Timestamp.newBuilder().setSeconds(threshold).build();
            Set<String> fromT = streamPairs(client, parent, location, roots, SearchDirection.UPSTREAM, 1, 0,
                    Interval.newBuilder().setStartTime(at).build());
            Set<String> untilT = streamPairs(client, parent, location, roots, SearchDirection.UPSTREAM, 1, 0,
                    Interval.newBuilder().setEndTime(at).build());
            out.println("T=" + Instant.ofEpochSecond(threshold));
            out.println("  start_time=T: returned=" + fromT.size()
                    + " | matches end>=T: " + fromT.equals(select(upstream, l -> l.getEndTime().getSeconds() >= threshold))
                    + " | matches start>=T: " + fromT.equals(select(upstream, l -> l.getStartTime().getSeconds() >= threshold))
                    + " | counts end>=T=" + select(upstream, l -> l.getEndTime().getSeconds() >= threshold).size()
                    + " start>=T=" + select(upstream, l -> l.getStartTime().getSeconds() >= threshold).size());
            out.println("  end_time=T: returned=" + untilT.size()
                    + " | matches start<=T: " + untilT.equals(select(upstream, l -> l.getStartTime().getSeconds() <= threshold))
                    + " | matches end<=T: " + untilT.equals(select(upstream, l -> l.getEndTime().getSeconds() <= threshold))
                    + " | counts start<=T=" + select(upstream, l -> l.getStartTime().getSeconds() <= threshold).size()
                    + " end<=T=" + select(upstream, l -> l.getEndTime().getSeconds() <= threshold).size());
        }
    }

    private void processesAndRuns(LineageClient client, String parent) {
        List<Process> newest = new ArrayList<>();
        client.listProcesses(ListProcessesRequest.newBuilder().setParent(parent).setPageSize(20).build())
                .getPage().getValues().forEach(newest::add);
        calls++;
        out.println("newest processes (first page of 20):");
        for (Process process : newest) {
            out.println("  " + process.getName() + " [" + process.getDisplayName() + "] " + process.getOrigin().getSourceType()
                    + " " + process.getAttributesMap());
        }
        for (Process process : newest.stream().limit(5).toList()) {
            List<Run> runs = new ArrayList<>();
            client.listRuns(ListRunsRequest.newBuilder().setParent(process.getName()).setPageSize(20).build())
                    .getPage().getValues().forEach(runs::add);
            calls++;
            out.println("runs of " + process.getName() + ": " + runs.size() + (runs.size() == 20 ? "+" : ""));
            runs.stream().limit(5).forEach(run -> out.println("  " + run.getState() + " "
                    + Instant.ofEpochSecond(run.getStartTime().getSeconds()) + " " + run.getAttributesMap()));
        }
    }

    private static Set<String> select(Map<String, Link> links, java.util.function.Predicate<Link> test) {
        Set<String> selected = new LinkedHashSet<>();
        links.forEach((key, link) -> {
            if (test.test(link)) selected.add(key);
        });
        return selected;
    }

    private static long midpoint(TreeSet<Long> values) {
        if (values.size() < 2) return Long.MIN_VALUE;
        List<Long> sorted = new ArrayList<>(values);
        int middle = sorted.size() / 2;
        return (sorted.get(middle - 1) + sorted.get(middle)) / 2;
    }

    private static String pair(LineageLink link) {
        return link.getSource().getFullyQualifiedName() + " -> " + link.getTarget().getFullyQualifiedName();
    }

    private static String describe(Interval range) {
        return (range.hasStartTime() ? "start=" + Instant.ofEpochSecond(range.getStartTime().getSeconds()) : "")
                + (range.hasEndTime() ? "end=" + Instant.ofEpochSecond(range.getEndTime().getSeconds()) : "");
    }

    private void section(String title) {
        out.println();
        out.println("== " + title);
    }

    private static LineageSettings settings(String project) throws Exception {
        String token = System.getenv("DATAPLEX_PROBE_TOKEN");
        GoogleCredentials credentials = token != null && !token.isBlank()
                ? GoogleCredentials.create(new AccessToken(token, null))
                : GoogleCredentials.getApplicationDefault();
        return LineageSettings.newHttpJsonBuilder()
                .setCredentialsProvider(FixedCredentialsProvider.create(credentials.createWithQuotaProject(project)))
                .build();
    }
}
