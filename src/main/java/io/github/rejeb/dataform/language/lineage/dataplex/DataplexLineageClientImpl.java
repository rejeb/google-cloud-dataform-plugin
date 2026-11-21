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

import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.StatusCode;
import com.google.cloud.datacatalog.lineage.v1.BatchSearchLinkProcessesRequest;
import com.google.cloud.datacatalog.lineage.v1.DependencyType;
import com.google.cloud.datacatalog.lineage.v1.EntityReference;
import com.google.cloud.datacatalog.lineage.v1.LineageClient;
import com.google.cloud.datacatalog.lineage.v1.LineageLink;
import com.google.cloud.datacatalog.lineage.v1.Link;
import com.google.cloud.datacatalog.lineage.v1.ListProcessesRequest;
import com.google.cloud.datacatalog.lineage.v1.ListRunsRequest;
import com.google.cloud.datacatalog.lineage.v1.MultipleEntityReference;
import com.google.cloud.datacatalog.lineage.v1.Origin;
import com.google.cloud.datacatalog.lineage.v1.Process;
import com.google.cloud.datacatalog.lineage.v1.ProcessLinkInfo;
import com.google.cloud.datacatalog.lineage.v1.ProcessLinks;
import com.google.cloud.datacatalog.lineage.v1.Run;
import com.google.cloud.datacatalog.lineage.v1.SearchLineageStreamingRequest;
import com.google.cloud.datacatalog.lineage.v1.SearchLineageStreamingRequest.RootCriteria;
import com.google.cloud.datacatalog.lineage.v1.SearchLineageStreamingRequest.SearchDirection;
import com.google.cloud.datacatalog.lineage.v1.SearchLineageStreamingRequest.SearchFilters;
import com.google.cloud.datacatalog.lineage.v1.SearchLineageStreamingRequest.SearchLimits;
import com.google.cloud.datacatalog.lineage.v1.SearchLineageStreamingResponse;
import com.google.cloud.datacatalog.lineage.v1.SearchLinksRequest;
import com.google.protobuf.Timestamp;
import com.google.protobuf.Value;
import com.google.type.Interval;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import io.github.rejeb.dataform.language.gcp.auth.AuthTrigger;
import io.github.rejeb.dataform.language.gcp.auth.GcpAuthErrors;
import io.github.rejeb.dataform.language.gcp.auth.GcpCalls;
import io.github.rejeb.dataform.language.lineage.dataplex.DataplexLineageException.Kind;
import io.github.rejeb.dataform.language.util.GcpClientsUtils;
import io.github.rejeb.dataform.language.util.concurrent.ParallelCalls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * {@link DataplexLineageClient} over the Google Data Lineage client. Calls that overlap share one
 * client per billed project, opened with the credential of the plugin and closed when the last of
 * them ends; the requests of one call run together.
 */
public final class DataplexLineageClientImpl implements DataplexLineageClient {

    private static final int PAGE_SIZE = 100;
    private static final int LINKS_PER_BATCH = 100;
    private static final String JOB_ID_ATTRIBUTE = "bigquery_job_id";
    private static final String SERVICE_DISABLED = "SERVICE_DISABLED";
    static final int ROOTS_PER_STREAM = 20;
    static final int MAX_RESULTS = 10_000;
    private static final Object LEASE_LOCK = new Object();
    private static final Map<String, Lease> LEASES = new HashMap<>();

    private final ParallelCalls calls;

    public DataplexLineageClientImpl() {
        this(ParallelCalls.shared());
    }

    DataplexLineageClientImpl(@NotNull ParallelCalls calls) {
        this.calls = calls;
    }

    @Override
    public @NotNull List<RecordedLink> links(@NotNull LineageScope scope, @NotNull String entityFqn,
                                             @NotNull Direction direction) {
        return call(scope.projectId(), client -> {
            SearchLinksRequest.Builder request = SearchLinksRequest.newBuilder()
                    .setParent(scope.parent()).setPageSize(PAGE_SIZE);
            EntityReference entity = EntityReference.newBuilder().setFullyQualifiedName(entityFqn).build();
            if (direction == Direction.UPSTREAM) {
                request.setTarget(entity);
            } else {
                request.setSource(entity);
            }
            List<RecordedLink> links = new ArrayList<>();
            for (Link link : client.searchLinks(request.build()).iterateAll()) {
                ProgressManager.checkCanceled();
                links.add(toRecordedLink(link));
            }
            return links;
        });
    }

    @Override
    public @NotNull Map<String, List<String>> processNames(@NotNull LineageScope scope, @NotNull List<String> linkNames) {
        if (linkNames.isEmpty()) return Map.of();
        return call(scope.projectId(), client -> {
            Map<String, List<String>> byLink = new LinkedHashMap<>();
            for (int from = 0; from < linkNames.size(); from += LINKS_PER_BATCH) {
                List<String> batch = linkNames.subList(from, Math.min(from + LINKS_PER_BATCH, linkNames.size()));
                BatchSearchLinkProcessesRequest request = BatchSearchLinkProcessesRequest.newBuilder()
                        .setParent(scope.parent()).addAllLinks(batch).setPageSize(PAGE_SIZE).build();
                for (ProcessLinks processLinks : client.batchSearchLinkProcesses(request).iterateAll()) {
                    ProgressManager.checkCanceled();
                    for (ProcessLinkInfo info : processLinks.getLinksList()) {
                        List<String> ofLink = byLink.computeIfAbsent(info.getLink(), k -> new ArrayList<>());
                        if (!ofLink.contains(processLinks.getProcess())) ofLink.add(processLinks.getProcess());
                    }
                }
            }
            return byLink;
        });
    }

    @Override
    public @NotNull RecordedProcess process(@NotNull String processName) {
        return call(GcpClientsUtils.projectIdFromResourceName(processName),
                client -> toRecordedProcess(client.getProcess(processName)));
    }

    @Override
    public @NotNull List<RecordedRun> runs(@NotNull String processName, int limit) {
        if (limit <= 0) return List.of();
        return call(GcpClientsUtils.projectIdFromResourceName(processName), client -> {
            ListRunsRequest request = ListRunsRequest.newBuilder()
                    .setParent(processName).setPageSize(Math.min(limit, PAGE_SIZE)).build();
            List<RecordedRun> runs = new ArrayList<>();
            for (Run run : client.listRuns(request).iterateAll()) {
                ProgressManager.checkCanceled();
                runs.add(toRecordedRun(run));
                if (runs.size() == limit) break;
            }
            return runs;
        });
    }

    @Override
    public @NotNull StreamAnswer stream(@NotNull LineageScope scope, @NotNull List<String> rootFqns,
                                        @NotNull Set<Direction> directions, int maxDepth, @Nullable Instant since) {
        if (rootFqns.isEmpty() || directions.isEmpty()) return new StreamAnswer(List.of(), false);
        return call(scope.projectId(), client -> stream(request -> read(client, request), calls, scope, rootFqns,
                directions, maxDepth, since));
    }

    @Override
    public @NotNull List<RecordedColumnLink> columnStream(@NotNull LineageScope scope,
                                                          @NotNull Map<String, List<String>> rootColumns,
                                                          @NotNull Set<Direction> directions, int maxDepth) {
        if (rootColumns.isEmpty() || directions.isEmpty()) return List.of();
        return call(scope.projectId(), client -> columnStream(request -> read(client, request), calls, scope, rootColumns,
                directions, maxDepth));
    }

    static @NotNull List<RecordedColumnLink> columnStream(@NotNull StreamCall call, @NotNull ParallelCalls calls,
                                                          @NotNull LineageScope scope,
                                                          @NotNull Map<String, List<String>> rootColumns,
                                                          @NotNull Set<Direction> directions, int maxDepth) {
        List<EntityReference> roots = new ArrayList<>();
        rootColumns.forEach((fqn, columns) -> columns.forEach(column ->
                roots.add(EntityReference.newBuilder().setFullyQualifiedName(fqn).addField(column).build())));
        List<RecordedColumnLink> links = new ArrayList<>();
        for (Searched part : searchAll(call, calls, scope, roots, directions, maxDepth, null)) {
            for (LineageLink link : part.links()) {
                RecordedColumnLink columnLink = toRecordedColumnLink(link);
                if (columnLink != null) links.add(columnLink);
            }
        }
        return links;
    }

    @Override
    public @Nullable String newestProcess(@NotNull LineageScope scope) {
        return call(scope.projectId(), client -> {
            ListProcessesRequest request = ListProcessesRequest.newBuilder()
                    .setParent(scope.parent()).setPageSize(1).build();
            for (Process process : client.listProcesses(request).getPage().getValues()) return process.getName();
            return null;
        });
    }

    private static @NotNull List<LineageLink> read(@NotNull LineageClient client,
                                                   @NotNull SearchLineageStreamingRequest request) {
        List<LineageLink> links = new ArrayList<>();
        for (SearchLineageStreamingResponse response : client.searchLineageStreamingCallable().call(request)) {
            ProgressManager.checkCanceled();
            links.addAll(response.getLinksList());
        }
        return links;
    }

    static @NotNull StreamAnswer stream(@NotNull StreamCall call, @NotNull ParallelCalls calls, @NotNull LineageScope scope,
                                        @NotNull List<String> rootFqns, @NotNull Set<Direction> directions, int maxDepth,
                                        @Nullable Instant since) {
        List<EntityReference> roots = new ArrayList<>(rootFqns.size());
        for (String root : rootFqns) roots.add(EntityReference.newBuilder().setFullyQualifiedName(root).build());
        List<StreamedLink> links = new ArrayList<>();
        boolean truncated = false;
        for (Searched part : searchAll(call, calls, scope, roots, directions, maxDepth, since)) {
            for (LineageLink link : part.links()) links.add(toStreamedLink(link, part.direction()));
            truncated |= part.truncated();
        }
        return new StreamAnswer(links, truncated);
    }

    private static @NotNull List<Searched> searchAll(@NotNull StreamCall call, @NotNull ParallelCalls calls,
                                                     @NotNull LineageScope scope, @NotNull List<EntityReference> roots,
                                                     @NotNull Set<Direction> directions, int maxDepth,
                                                     @Nullable Instant since) {
        List<Callable<Searched>> searches = new ArrayList<>();
        for (Direction direction : EnumSet.copyOf(directions)) {
            for (int from = 0; from < roots.size(); from += ROOTS_PER_STREAM) {
                List<EntityReference> batch = List.copyOf(roots.subList(from, Math.min(from + ROOTS_PER_STREAM, roots.size())));
                searches.add(() -> search(call, scope, batch, direction, maxDepth, since));
            }
        }
        return calls.all(searches);
    }

    private static @NotNull Searched search(@NotNull StreamCall call, @NotNull LineageScope scope,
                                            @NotNull List<EntityReference> roots, @NotNull Direction direction,
                                            int maxDepth, @Nullable Instant since) throws Exception {
        List<LineageLink> found = call.call(streamRequest(scope, roots, direction, maxDepth, since));
        if (found.size() < MAX_RESULTS || roots.size() == 1) {
            return new Searched(direction, found, found.size() >= MAX_RESULTS);
        }
        int half = roots.size() / 2;
        Searched first = search(call, scope, roots.subList(0, half), direction, maxDepth, since);
        Searched second = search(call, scope, roots.subList(half, roots.size()), direction, maxDepth, since);
        List<LineageLink> links = new ArrayList<>(first.links());
        links.addAll(second.links());
        return new Searched(direction, links, first.truncated() || second.truncated());
    }

    static @NotNull SearchLineageStreamingRequest streamRequest(@NotNull LineageScope scope,
                                                                @NotNull List<EntityReference> roots,
                                                                @NotNull Direction direction, int maxDepth,
                                                                @Nullable Instant since) {
        MultipleEntityReference.Builder entities = MultipleEntityReference.newBuilder().addAllEntities(roots);
        SearchLineageStreamingRequest.Builder request = SearchLineageStreamingRequest.newBuilder()
                .setParent(scope.parent())
                .addLocations(scope.location().toLowerCase(Locale.ROOT))
                .setRootCriteria(RootCriteria.newBuilder().setEntities(entities))
                .setDirection(direction == Direction.UPSTREAM ? SearchDirection.UPSTREAM : SearchDirection.DOWNSTREAM)
                .setLimits(SearchLimits.newBuilder().setMaxDepth(maxDepth).setMaxResults(MAX_RESULTS));
        if (since != null) {
            Timestamp start = Timestamp.newBuilder().setSeconds(since.getEpochSecond()).setNanos(since.getNano()).build();
            request.setFilters(SearchFilters.newBuilder().setTimeRange(Interval.newBuilder().setStartTime(start)));
        }
        return request.build();
    }

    static @NotNull StreamedLink toStreamedLink(@NotNull LineageLink link, @NotNull Direction direction) {
        return new StreamedLink(link.getSource().getFullyQualifiedName(), link.getTarget().getFullyQualifiedName(),
                link.getDepth(), direction);
    }

    private static <T> T call(@Nullable String quotaProjectId, @NotNull ClientCall<T> body) {
        try {
            return GcpCalls.execute(AuthTrigger.BACKGROUND, () -> {
                String key = quotaProjectId == null ? "" : quotaProjectId;
                LineageClient client = acquire(key, quotaProjectId);
                try {
                    return body.apply(client);
                } finally {
                    release(key);
                }
            });
        } catch (ProcessCanceledException e) {
            throw e;
        } catch (RuntimeException e) {
            throw wrap(e);
        }
    }

    static @NotNull RecordedLink toRecordedLink(@NotNull Link link) {
        return new RecordedLink(link.getName(), link.getSource().getFullyQualifiedName(),
                link.getTarget().getFullyQualifiedName(), instant(link.getStartTime()), instant(link.getEndTime()));
    }

    static @NotNull RecordedProcess toRecordedProcess(@NotNull Process process) {
        Origin.SourceType origin = process.getOrigin().getSourceType();
        String displayName = process.getDisplayName().isBlank() ? defaultName(origin) : process.getDisplayName();
        Value jobId = process.getAttributesMap().get(JOB_ID_ATTRIBUTE);
        String latestJobId = jobId == null || jobId.getStringValue().isBlank() ? null : jobId.getStringValue();
        return new RecordedProcess(process.getName(), displayName, origin.name(), latestJobId);
    }

    private static @NotNull String defaultName(@NotNull Origin.SourceType origin) {
        return switch (origin) {
            case BIGQUERY -> "BigQuery job";
            case SOURCE_TYPE_UNSPECIFIED, CUSTOM, UNRECOGNIZED -> "Process";
            default -> {
                String words = origin.name().toLowerCase(Locale.ROOT).replace('_', ' ');
                yield Character.toUpperCase(words.charAt(0)) + words.substring(1) + " process";
            }
        };
    }

    static @NotNull RecordedRun toRecordedRun(@NotNull Run run) {
        RecordedRun.State state = switch (run.getState()) {
            case STARTED -> RecordedRun.State.STARTED;
            case COMPLETED -> RecordedRun.State.COMPLETED;
            case FAILED -> RecordedRun.State.FAILED;
            case ABORTED -> RecordedRun.State.ABORTED;
            default -> RecordedRun.State.UNKNOWN;
        };
        return new RecordedRun(run.getName(), state, instant(run.getStartTime()),
                run.hasEndTime() ? instant(run.getEndTime()) : null);
    }

    /**
     * @return the column link, or {@code null} when the streamed link is not between two top-level columns
     */
    static @Nullable RecordedColumnLink toRecordedColumnLink(@NotNull LineageLink link) {
        EntityReference source = link.getSource();
        EntityReference target = link.getTarget();
        if (source.getFieldCount() != 1 || target.getFieldCount() != 1) return null;
        boolean exactCopy = link.getDependencyInfoList().stream()
                .anyMatch(info -> info.getDependencyType() == DependencyType.EXACT_COPY);
        return new RecordedColumnLink(source.getFullyQualifiedName(), source.getField(0), target.getFullyQualifiedName(),
                target.getField(0), exactCopy);
    }

    static @NotNull DataplexLineageException wrap(@NotNull Throwable failure) {
        if (failure instanceof DataplexLineageException known) return known;
        String message = failure.getMessage();
        for (Throwable cause = failure; cause != null; cause = cause.getCause() == cause ? null : cause.getCause()) {
            if (cause instanceof ApiException && cause.getMessage() != null) {
                message = cause.getMessage();
                break;
            }
        }
        return new DataplexLineageException(kindOf(failure),
                message == null || message.isBlank() ? failure.getClass().getSimpleName() : message, failure);
    }

    static @NotNull Kind kindOf(@NotNull Throwable failure) {
        if (GcpAuthErrors.isAuthFailure(failure)) return Kind.NOT_SIGNED_IN;
        for (Throwable cause = failure; cause != null; cause = cause.getCause() == cause ? null : cause.getCause()) {
            if (cause instanceof ApiException api && api.getStatusCode().getCode() == StatusCode.Code.PERMISSION_DENIED) {
                return isServiceDisabled(api) ? Kind.API_DISABLED : Kind.PERMISSION_DENIED;
            }
        }
        return Kind.OTHER;
    }

    private static boolean isServiceDisabled(@NotNull ApiException failure) {
        if (SERVICE_DISABLED.equals(failure.getReason())) return true;
        String message = failure.getMessage();
        if (message == null) return false;
        String lower = message.toLowerCase(Locale.ROOT);
        return lower.contains("service_disabled") || lower.contains("has not been used in project")
                || lower.contains("it is disabled");
    }

    private static @NotNull Instant instant(@NotNull Timestamp timestamp) {
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }

    private static @NotNull LineageClient acquire(@NotNull String key, @Nullable String quotaProjectId) throws IOException {
        synchronized (LEASE_LOCK) {
            Lease lease = LEASES.get(key);
            if (lease == null) {
                lease = new Lease(GcpClientsUtils.lineageClient(quotaProjectId));
                LEASES.put(key, lease);
            }
            lease.users++;
            return lease.client;
        }
    }

    private static void release(@NotNull String key) {
        LineageClient closing = null;
        synchronized (LEASE_LOCK) {
            Lease lease = LEASES.get(key);
            if (lease != null && --lease.users == 0) {
                LEASES.remove(key);
                closing = lease.client;
            }
        }
        if (closing != null) closing.close();
    }

    @FunctionalInterface
    private interface ClientCall<T> {
        T apply(@NotNull LineageClient client) throws Exception;
    }

    @FunctionalInterface
    interface StreamCall {
        @NotNull List<LineageLink> call(@NotNull SearchLineageStreamingRequest request) throws Exception;
    }

    private record Searched(@NotNull Direction direction, @NotNull List<LineageLink> links, boolean truncated) {
    }

    private static final class Lease {
        private final LineageClient client;
        private int users;

        private Lease(@NotNull LineageClient client) {
            this.client = client;
        }
    }
}
