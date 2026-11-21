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
import com.google.api.gax.rpc.ApiExceptionFactory;
import com.google.api.gax.rpc.ErrorDetails;
import com.google.api.gax.rpc.StatusCode;
import com.google.cloud.datacatalog.lineage.v1.DependencyType;
import com.google.cloud.datacatalog.lineage.v1.EntityReference;
import com.google.cloud.datacatalog.lineage.v1.LineageLink;
import com.google.cloud.datacatalog.lineage.v1.Link;
import com.google.cloud.datacatalog.lineage.v1.Origin;
import com.google.cloud.datacatalog.lineage.v1.Process;
import com.google.cloud.datacatalog.lineage.v1.Run;
import com.google.cloud.datacatalog.lineage.v1.SearchLineageStreamingRequest;
import com.google.cloud.datacatalog.lineage.v1.SearchLineageStreamingRequest.SearchDirection;
import com.google.protobuf.Any;
import com.google.protobuf.Timestamp;
import com.google.protobuf.Value;
import com.google.rpc.ErrorInfo;
import io.github.rejeb.dataform.language.gcp.auth.GcpAuthRequiredException;
import io.github.rejeb.dataform.language.lineage.dataplex.DataplexLineageClient.Direction;
import io.github.rejeb.dataform.language.lineage.dataplex.DataplexLineageException.Kind;
import io.github.rejeb.dataform.language.util.concurrent.ParallelCalls;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataplexLineageClientImplTest {

    private static EntityReference entity(String fqn, String... fields) {
        return EntityReference.newBuilder().setFullyQualifiedName(fqn).addAllField(List.of(fields)).build();
    }

    private static Timestamp at(long seconds) {
        return Timestamp.newBuilder().setSeconds(seconds).build();
    }

    private static ApiException apiException(StatusCode.Code code, String message, String reason) {
        StatusCode status = new StatusCode() {
            @Override
            public Code getCode() {
                return code;
            }

            @Override
            public Object getTransportCode() {
                return code;
            }
        };
        ErrorDetails details = reason == null ? null : ErrorDetails.builder()
                .setRawErrorMessages(List.of(Any.pack(ErrorInfo.newBuilder().setReason(reason)
                        .setDomain("googleapis.com").build())))
                .build();
        return ApiExceptionFactory.createException(new RuntimeException(message), status, false, details);
    }

    @Test
    void aLinkKeepsItsNameEndsAndTimes() {
        RecordedLink link = DataplexLineageClientImpl.toRecordedLink(Link.newBuilder()
                .setName("projects/p/locations/eu/links/abc")
                .setSource(entity("bigquery:p.d.src")).setTarget(entity("bigquery:p.d.out"))
                .setStartTime(at(1_000)).setEndTime(at(2_000)).build());

        assertEquals(new RecordedLink("projects/p/locations/eu/links/abc", "bigquery:p.d.src", "bigquery:p.d.out",
                Instant.ofEpochSecond(1_000), Instant.ofEpochSecond(2_000)), link);
    }

    @Test
    void aProcessKeepsItsDisplayName() {
        RecordedProcess process = DataplexLineageClientImpl.toRecordedProcess(Process.newBuilder()
                .setName("projects/p/locations/eu/processes/1").setDisplayName("nightly load")
                .setOrigin(Origin.newBuilder().setSourceType(Origin.SourceType.COMPOSER)).build());

        assertEquals("nightly load", process.displayName());
        assertEquals("COMPOSER", process.origin());
        assertNull(process.latestJobId());
    }

    @Test
    void aProcessWithoutDisplayNameAndBigQueryOriginReadsBigQueryJob() {
        RecordedProcess process = DataplexLineageClientImpl.toRecordedProcess(Process.newBuilder()
                .setName("projects/p/locations/eu/processes/1")
                .setOrigin(Origin.newBuilder().setSourceType(Origin.SourceType.BIGQUERY)).build());

        assertEquals("BigQuery job", process.displayName());
    }

    @Test
    void aProcessWithoutDisplayNameAndAnotherOriginReadsItsOrigin() {
        RecordedProcess process = DataplexLineageClientImpl.toRecordedProcess(Process.newBuilder()
                .setName("projects/p/locations/eu/processes/1")
                .setOrigin(Origin.newBuilder().setSourceType(Origin.SourceType.DATAFLOW)).build());

        assertEquals("Dataflow process", process.displayName());
    }

    @Test
    void theLatestJobIdComesFromTheProcessAttributes() {
        RecordedProcess process = DataplexLineageClientImpl.toRecordedProcess(Process.newBuilder()
                .setName("projects/p/locations/eu/processes/1")
                .setOrigin(Origin.newBuilder().setSourceType(Origin.SourceType.BIGQUERY))
                .putAttributes("bigquery_job_id", Value.newBuilder().setStringValue("job_123").build()).build());

        assertEquals("job_123", process.latestJobId());
    }

    @Test
    void aRunKeepsItsStateAndTimes() {
        RecordedRun run = DataplexLineageClientImpl.toRecordedRun(Run.newBuilder()
                .setName("projects/p/locations/eu/processes/1/runs/9").setState(Run.State.FAILED)
                .setStartTime(at(10)).setEndTime(at(70)).build());

        assertEquals(new RecordedRun("projects/p/locations/eu/processes/1/runs/9", RecordedRun.State.FAILED,
                Instant.ofEpochSecond(10), Instant.ofEpochSecond(70)), run);
    }

    @Test
    void aRunStillGoingHasNoEnd() {
        RecordedRun run = DataplexLineageClientImpl.toRecordedRun(Run.newBuilder()
                .setName("r").setState(Run.State.STARTED).setStartTime(at(10)).build());

        assertEquals(RecordedRun.State.STARTED, run.state());
        assertNull(run.end());
    }

    @Test
    void aColumnLinkKeepsBothColumns() {
        RecordedColumnLink link = DataplexLineageClientImpl.toRecordedColumnLink(LineageLink.newBuilder()
                .setSource(entity("bigquery:p.d.src", "amount")).setTarget(entity("bigquery:p.d.out", "total"))
                .addDependencyInfo(LineageLink.DependencyInfo.newBuilder().setDependencyType(DependencyType.OTHER)).build());

        assertEquals(new RecordedColumnLink("bigquery:p.d.src", "amount", "bigquery:p.d.out", "total", false), link);
    }

    @Test
    void aColumnLinkWithExactCopyIsMarkedExact() {
        RecordedColumnLink link = DataplexLineageClientImpl.toRecordedColumnLink(LineageLink.newBuilder()
                .setSource(entity("bigquery:p.d.src", "amount")).setTarget(entity("bigquery:p.d.out", "amount"))
                .addDependencyInfo(LineageLink.DependencyInfo.newBuilder().setDependencyType(DependencyType.EXACT_COPY)).build());

        assertNotNull(link);
        assertTrue(link.exactCopy());
    }

    @Test
    void aLinkWithNestedFieldsIsSkipped() {
        assertNull(DataplexLineageClientImpl.toRecordedColumnLink(LineageLink.newBuilder()
                .setSource(entity("bigquery:p.d.src", "address", "city")).setTarget(entity("bigquery:p.d.out", "city"))
                .build()));
    }

    @Test
    void aTableLinkIsNotAColumnLink() {
        assertNull(DataplexLineageClientImpl.toRecordedColumnLink(LineageLink.newBuilder()
                .setSource(entity("bigquery:p.d.src")).setTarget(entity("bigquery:p.d.out")).build()));
    }

    @Test
    void permissionDeniedWithServiceDisabledReasonIsApiDisabled() {
        assertEquals(Kind.API_DISABLED, DataplexLineageClientImpl.kindOf(
                apiException(StatusCode.Code.PERMISSION_DENIED, "denied", "SERVICE_DISABLED")));
    }

    @Test
    void permissionDeniedSayingTheApiWasNotUsedIsApiDisabled() {
        assertEquals(Kind.API_DISABLED, DataplexLineageClientImpl.kindOf(apiException(StatusCode.Code.PERMISSION_DENIED,
                "Data Lineage API has not been used in project 123 before or it is disabled.", null)));
    }

    @Test
    void permissionDeniedOtherwiseIsPermissionDenied() {
        assertEquals(Kind.PERMISSION_DENIED, DataplexLineageClientImpl.kindOf(apiException(
                StatusCode.Code.PERMISSION_DENIED, "Permission datalineage.locations.searchLinks denied", null)));
    }

    @Test
    void aMissingCredentialIsNotSignedIn() {
        assertEquals(Kind.NOT_SIGNED_IN, DataplexLineageClientImpl.kindOf(new GcpAuthRequiredException("sign in")));
        assertEquals(Kind.NOT_SIGNED_IN, DataplexLineageClientImpl.kindOf(
                new RuntimeException(apiException(StatusCode.Code.UNAUTHENTICATED, "no token", null))));
    }

    @Test
    void anyOtherFailureIsOther() {
        assertEquals(Kind.OTHER, DataplexLineageClientImpl.kindOf(new IllegalStateException("boom")));
        assertEquals(Kind.OTHER, DataplexLineageClientImpl.kindOf(
                apiException(StatusCode.Code.UNAVAILABLE, "try later", null)));
    }

    @Test
    void theScopeNamesItsParentInLowerCase() {
        assertEquals("projects/my-project/locations/eu", new LineageScope("my-project", "EU").parent());
    }

    @Test
    void theExceptionKeepsItsKindAndCause() {
        IllegalStateException cause = new IllegalStateException("boom");
        DataplexLineageException wrapped = DataplexLineageClientImpl.wrap(cause);

        assertEquals(Kind.OTHER, wrapped.kind());
        assertEquals(cause, wrapped.getCause());
        assertFalse(wrapped.getMessage().isBlank());
    }

    @Test
    void anAlreadyWrappedFailureIsNotWrappedAgain() {
        DataplexLineageException first = new DataplexLineageException(Kind.PERMISSION_DENIED, "denied", null);
        assertEquals(first, DataplexLineageClientImpl.wrap(first));
    }

    private static List<String> rootsOf(SearchLineageStreamingRequest request) {
        return request.getRootCriteria().getEntities().getEntitiesList().stream()
                .map(EntityReference::getFullyQualifiedName).toList();
    }

    @Test
    void streamRequestCarriesRootsDepthResultsAndSince() {
        Instant since = Instant.parse("2026-11-06T00:00:00Z");

        SearchLineageStreamingRequest request = DataplexLineageClientImpl.streamRequest(
                new LineageScope("p", "eu"), List.of(entity("bigquery:p.d.a"), entity("bigquery:p.d.b")), Direction.UPSTREAM, 3, since);

        assertEquals("projects/p/locations/eu", request.getParent());
        assertEquals(List.of("bigquery:p.d.a", "bigquery:p.d.b"), rootsOf(request));
        assertEquals(SearchDirection.UPSTREAM, request.getDirection());
        assertEquals(3, request.getLimits().getMaxDepth());
        assertEquals(10_000, request.getLimits().getMaxResults());
        assertEquals(since.getEpochSecond(), request.getFilters().getTimeRange().getStartTime().getSeconds());
        assertFalse(request.getFilters().getTimeRange().hasEndTime());
    }

    @Test
    void streamRequestLowerCasesTheLocation() {
        SearchLineageStreamingRequest request = DataplexLineageClientImpl.streamRequest(
                new LineageScope("p", "EU"), List.of(entity("bigquery:p.d.a")), Direction.DOWNSTREAM, 3, null);

        assertEquals(List.of("eu"), request.getLocationsList());
        assertEquals("projects/p/locations/eu", request.getParent());
        assertEquals(SearchDirection.DOWNSTREAM, request.getDirection());
    }

    @Test
    void streamRequestWithoutSinceHasNoTimeRange() {
        SearchLineageStreamingRequest request = DataplexLineageClientImpl.streamRequest(
                new LineageScope("p", "eu"), List.of(entity("bigquery:p.d.a")), Direction.UPSTREAM, 3, null);

        assertFalse(request.getFilters().hasTimeRange());
    }

    @Test
    void aStreamedLinkKeepsItsEndsDepthAndDirection() {
        StreamedLink link = DataplexLineageClientImpl.toStreamedLink(LineageLink.newBuilder()
                .setSource(entity("bigquery:p.d.src")).setTarget(entity("bigquery:p.d.out")).setDepth(2).build(),
                Direction.UPSTREAM);

        assertEquals(new StreamedLink("bigquery:p.d.src", "bigquery:p.d.out", 2, Direction.UPSTREAM), link);
    }

    @Test
    void aFullBatchIsSplitAndOnlyASingleFullRootTruncates() {
        List<List<String>> asked = Collections.synchronizedList(new ArrayList<>());
        DataplexLineageClientImpl.StreamCall call = request -> {
            List<String> roots = rootsOf(request);
            asked.add(roots);
            int count = roots.contains("big") ? 10_000 : 1;
            List<LineageLink> links = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                links.add(LineageLink.newBuilder().setSource(entity("s" + i)).setTarget(entity(roots.getFirst()))
                        .setDepth(1).build());
            }
            return links;
        };
        LineageScope scope = new LineageScope("p", "eu");

        StreamAnswer withBig = DataplexLineageClientImpl.stream(call, ParallelCalls.direct(), scope,
                List.of("a", "big", "c"), EnumSet.of(Direction.UPSTREAM), 3, null);

        assertTrue(withBig.truncated());
        assertEquals(List.of(List.of("a", "big", "c"), List.of("a"), List.of("big", "c"), List.of("big"), List.of("c")),
                asked);

        asked.clear();
        StreamAnswer withoutBig = DataplexLineageClientImpl.stream(call, ParallelCalls.direct(), scope,
                List.of("a", "c"), EnumSet.of(Direction.UPSTREAM), 3, null);

        assertFalse(withoutBig.truncated());
        assertEquals(List.of(List.of("a", "c")), asked);
        assertEquals(1, withoutBig.links().size());
    }

    @Test
    void rootsAreAskedInBatchesOfTwentyForEachDirectionAndMergedInOrder() {
        List<String> roots = new ArrayList<>();
        for (int i = 0; i < 25; i++) roots.add("t" + i);
        DataplexLineageClientImpl.StreamCall call = request -> List.of(LineageLink.newBuilder()
                .setSource(entity(request.getDirection() + "-" + rootsOf(request).getFirst()))
                .setTarget(entity(rootsOf(request).getFirst())).setDepth(1).build());

        StreamAnswer answer = DataplexLineageClientImpl.stream(call, ParallelCalls.shared(), new LineageScope("p", "eu"),
                roots, EnumSet.allOf(Direction.class), 3, null);

        assertEquals(List.of("UPSTREAM-t0", "UPSTREAM-t20", "DOWNSTREAM-t0", "DOWNSTREAM-t20"),
                answer.links().stream().map(StreamedLink::sourceFqn).toList());
        assertEquals(List.of(Direction.UPSTREAM, Direction.UPSTREAM, Direction.DOWNSTREAM, Direction.DOWNSTREAM),
                answer.links().stream().map(StreamedLink::direction).toList());
    }

    private static List<String> columnRootsOf(SearchLineageStreamingRequest request) {
        return request.getRootCriteria().getEntities().getEntitiesList().stream()
                .map(entity -> entity.getFullyQualifiedName() + "#" + String.join(",", entity.getFieldList())).toList();
    }

    @Test
    void columnRootsAreAskedWithTheirColumnInBatchesOfTwentyForEachDirection() {
        List<String> many = new ArrayList<>();
        for (int i = 0; i < 22; i++) many.add("c" + i);
        Map<String, List<String>> roots = new LinkedHashMap<>();
        roots.put("bigquery:p.d.a", many);
        roots.put("bigquery:p.d.b", List.of("x", "y", "z"));
        List<SearchLineageStreamingRequest> asked = new ArrayList<>();
        DataplexLineageClientImpl.StreamCall call = request -> {
            asked.add(request);
            return List.of(LineageLink.newBuilder().setSource(entity("bigquery:p.d.s", request.getDirection().name()))
                    .setTarget(request.getRootCriteria().getEntities().getEntities(0)).setDepth(1).build());
        };

        List<RecordedColumnLink> links = DataplexLineageClientImpl.columnStream(call, ParallelCalls.direct(),
                new LineageScope("p", "eu"), roots, EnumSet.allOf(Direction.class), 100);

        List<String> first = many.subList(0, 20).stream().map(column -> "bigquery:p.d.a#" + column).toList();
        List<String> second = List.of("bigquery:p.d.a#c20", "bigquery:p.d.a#c21", "bigquery:p.d.b#x", "bigquery:p.d.b#y",
                "bigquery:p.d.b#z");
        assertEquals(List.of(first, second, first, second),
                asked.stream().map(DataplexLineageClientImplTest::columnRootsOf).toList());
        assertEquals(List.of(SearchDirection.UPSTREAM, SearchDirection.UPSTREAM, SearchDirection.DOWNSTREAM,
                SearchDirection.DOWNSTREAM), asked.stream().map(SearchLineageStreamingRequest::getDirection).toList());
        assertTrue(asked.stream().allMatch(request -> request.getLimits().getMaxDepth() == 100
                && request.getLimits().getMaxResults() == 10_000 && !request.getFilters().hasTimeRange()
                && request.getParent().equals("projects/p/locations/eu")));
        assertEquals(List.of("UPSTREAM>c0", "UPSTREAM>c20", "DOWNSTREAM>c0", "DOWNSTREAM>c20"),
                links.stream().map(link -> link.sourceColumn() + ">" + link.targetColumn()).toList());
    }

    @Test
    void aFullBatchOfColumnsIsSplitInHalves() {
        List<List<String>> asked = new ArrayList<>();
        DataplexLineageClientImpl.StreamCall call = request -> {
            List<String> roots = columnRootsOf(request);
            asked.add(roots);
            int count = roots.contains("bigquery:p.d.a#big") ? 10_000 : 1;
            List<LineageLink> links = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                links.add(LineageLink.newBuilder().setSource(entity("bigquery:p.d.s", "s" + i))
                        .setTarget(request.getRootCriteria().getEntities().getEntities(0)).setDepth(1).build());
            }
            return links;
        };

        List<RecordedColumnLink> links = DataplexLineageClientImpl.columnStream(call, ParallelCalls.direct(),
                new LineageScope("p", "eu"), Map.of("bigquery:p.d.a", List.of("x", "big", "z")),
                EnumSet.of(Direction.UPSTREAM), 100);

        assertEquals(List.of(List.of("bigquery:p.d.a#x", "bigquery:p.d.a#big", "bigquery:p.d.a#z"),
                List.of("bigquery:p.d.a#x"), List.of("bigquery:p.d.a#big", "bigquery:p.d.a#z"),
                List.of("bigquery:p.d.a#big"), List.of("bigquery:p.d.a#z")), asked);
        assertEquals(10_002, links.size());
    }
}
