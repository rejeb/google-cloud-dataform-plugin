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

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import io.github.rejeb.dataform.language.lineage.dataplex.LineageScope;
import io.github.rejeb.dataform.language.lineage.dataplex.TimeRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Default {@link DataplexLineageCache}: the entries are held compressed in the component state,
 * which the platform writes to {@code cache-state.xml}; nothing decoded is kept.
 */
@State(name = "DataformKnowledgeCatalogLineage",
        storages = @Storage(value = StoragePathMacros.CACHE_FILE, roamingType = RoamingType.DISABLED))
public final class DataplexLineageCacheImpl implements DataplexLineageCache,
        PersistentStateComponent<DataplexLineageCacheImpl.State> {

    private final Object lock = new Object();
    private final Map<String, String> entries = new TreeMap<>();

    @Override
    public @Nullable LineageCacheEntry get(@NotNull LineageScope scope, @NotNull TimeRange range) {
        String key = keyOf(scope, range);
        String value;
        synchronized (lock) {
            value = entries.get(key);
        }
        if (value == null) return null;
        LineageCacheEntry entry = LineageCacheCodec.decode(value);
        if (entry == null) {
            synchronized (lock) {
                entries.remove(key, value);
            }
        }
        return entry;
    }

    @Override
    public void put(@NotNull LineageCacheEntry entry) {
        String value = LineageCacheCodec.encode(entry);
        synchronized (lock) {
            entries.put(keyOf(entry.scope(), entry.range()), value);
        }
    }

    @Override
    public void remove(@NotNull LineageScope scope, @NotNull TimeRange range) {
        synchronized (lock) {
            entries.remove(keyOf(scope, range));
        }
    }

    @Override
    public @NotNull State getState() {
        State state = new State();
        synchronized (lock) {
            state.entries.putAll(entries);
        }
        return state;
    }

    @Override
    public void loadState(@NotNull State state) {
        synchronized (lock) {
            entries.clear();
            if (state.entries != null) entries.putAll(state.entries);
        }
    }

    static @NotNull String keyOf(@NotNull LineageScope scope, @NotNull TimeRange range) {
        return scope.projectId() + '|' + scope.location().toLowerCase(Locale.ROOT) + '|' + range.name();
    }

    /**
     * The persisted form: the encoded entries by project, location and range.
     */
    public static final class State {
        public Map<String, String> entries = new TreeMap<>();
    }
}
