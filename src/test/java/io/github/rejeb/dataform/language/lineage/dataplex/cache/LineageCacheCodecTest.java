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

import io.github.rejeb.dataform.language.lineage.dataplex.DataplexLineageClient.Direction;
import io.github.rejeb.dataform.language.lineage.dataplex.DataplexSnapshot;
import io.github.rejeb.dataform.language.lineage.dataplex.LineageScope;
import io.github.rejeb.dataform.language.lineage.dataplex.RecordedLink;
import io.github.rejeb.dataform.language.lineage.dataplex.TimeRange;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LineageCacheCodecTest {

    static final Instant LOADED_AT = Instant.parse("2026-11-13T14:02:00Z");
    static final LineageScope SCOPE = new LineageScope("p", "EU");

    static LineageCacheEntry entry(int links) {
        List<RecordedLink> recorded = new ArrayList<>();
        Set<String> entities = new LinkedHashSet<>(List.of("bigquery:p.d.a", "bigquery:p.d.b"));
        for (int i = 0; i < links; i++) {
            recorded.add(RecordedLink.streamed("bigquery:other.raw.source_table_" + i, "bigquery:p.d.a"));
            entities.add("bigquery:other.raw.source_table_" + i);
        }
        recorded.add(new RecordedLink("projects/p/locations/eu/links/1", "bigquery:p.d.a", "bigquery:p.d.b",
                Instant.parse("2026-11-01T00:00:00Z"), Instant.parse("2026-11-12T00:00:00Z")));
        DataplexSnapshot snapshot = new DataplexSnapshot(new LinkedHashSet<>(List.of("bigquery:p.d.a", "bigquery:p.d.b")),
                entities, recorded, List.of(RecordedLink.streamed("bigquery:far.x.y", "bigquery:other.raw.source_table_0")),
                Set.of("bigquery:p.d.b"), Set.of(), Set.of("bigquery:x.y.z"), Set.of("bigquery:p.d.a"), true, LOADED_AT);
        return new LineageCacheEntry(SCOPE, TimeRange.LAST_7_DAYS, snapshot, "[a=table, b=view]", "processes/42",
                List.of(new Expansion("bigquery:p.d.b", Direction.DOWNSTREAM)));
    }

    @Test
    void anEntryRoundTripsWithItsExpansionsMarkerAndNullTimes() {
        LineageCacheEntry entry = entry(3);

        LineageCacheEntry decoded = LineageCacheCodec.decode(LineageCacheCodec.encode(entry));

        assertEquals(entry, decoded);
        assertEquals(List.copyOf(entry.snapshot().entities()), List.copyOf(decoded.snapshot().entities()));
        assertNull(decoded.snapshot().links().getFirst().firstSeen());
    }

    @Test
    void anEntryWithoutMarkerRoundTrips() {
        LineageCacheEntry entry = new LineageCacheEntry(SCOPE, TimeRange.LAST_30_DAYS, entry(0).snapshot(), "[]", null, List.of());

        assertEquals(entry, LineageCacheCodec.decode(LineageCacheCodec.encode(entry)));
    }

    @Test
    void aBrokenOrOlderValueIsDroppedAndGivesNoEntry() {
        assertNull(LineageCacheCodec.decode("x"));
        assertNull(LineageCacheCodec.decode(Base64.getEncoder().encodeToString("not gzip".getBytes(StandardCharsets.UTF_8))));
        assertNull(LineageCacheCodec.decode(LineageCacheCodec.encode(entry(1), LineageCacheCodec.VERSION - 1)));
    }

    @Test
    void theStateHoldsCompressedValues() {
        LineageCacheEntry entry = entry(500);

        assertTrue(LineageCacheCodec.encode(entry).length() < LineageCacheCodec.json(entry, LineageCacheCodec.VERSION).length());
    }
}
