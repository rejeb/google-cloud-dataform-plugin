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

import io.github.rejeb.dataform.language.lineage.dataplex.LineageScope;
import io.github.rejeb.dataform.language.lineage.dataplex.TimeRange;
import org.junit.jupiter.api.Test;

import static io.github.rejeb.dataform.language.lineage.dataplex.cache.LineageCacheCodecTest.entry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class DataplexLineageCacheImplTest {

    @Test
    void anEntryPutIsGivenBack() {
        DataplexLineageCacheImpl cache = new DataplexLineageCacheImpl();
        LineageCacheEntry entry = entry(2);

        cache.put(entry);

        assertEquals(entry, cache.get(entry.scope(), entry.range()));
        assertNull(cache.get(entry.scope(), TimeRange.LAST_30_DAYS));
    }

    @Test
    void entriesAreKeyedByProjectLocationAndRange() {
        DataplexLineageCacheImpl cache = new DataplexLineageCacheImpl();
        cache.put(entry(2));

        assertEquals(entry(2), cache.get(new LineageScope("p", "eu"), TimeRange.LAST_7_DAYS));
        assertNull(cache.get(new LineageScope("q", "EU"), TimeRange.LAST_7_DAYS));
        assertNull(cache.get(new LineageScope("p", "us"), TimeRange.LAST_7_DAYS));
    }

    @Test
    void aBrokenValueIsRemovedFromTheState() {
        DataplexLineageCacheImpl cache = new DataplexLineageCacheImpl();
        DataplexLineageCacheImpl.State state = new DataplexLineageCacheImpl.State();
        state.entries.put(DataplexLineageCacheImpl.keyOf(new LineageScope("p", "EU"), TimeRange.LAST_7_DAYS), "x");
        cache.loadState(state);

        assertNull(cache.get(new LineageScope("p", "EU"), TimeRange.LAST_7_DAYS));
        assertFalse(cache.getState().entries.containsKey(
                DataplexLineageCacheImpl.keyOf(new LineageScope("p", "EU"), TimeRange.LAST_7_DAYS)));
    }

    @Test
    void stateRoundTripsThroughGetStateAndLoadState() {
        DataplexLineageCacheImpl first = new DataplexLineageCacheImpl();
        first.put(entry(4));
        DataplexLineageCacheImpl second = new DataplexLineageCacheImpl();

        second.loadState(first.getState());

        assertEquals(entry(4), second.get(entry(4).scope(), entry(4).range()));
    }

    @Test
    void removeDropsTheEntry() {
        DataplexLineageCacheImpl cache = new DataplexLineageCacheImpl();
        cache.put(entry(2));

        cache.remove(entry(2).scope(), entry(2).range());

        assertNull(cache.get(entry(2).scope(), entry(2).range()));
    }
}
