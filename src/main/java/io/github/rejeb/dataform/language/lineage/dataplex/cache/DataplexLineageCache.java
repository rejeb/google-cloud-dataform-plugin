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

import com.intellij.openapi.project.Project;
import io.github.rejeb.dataform.language.lineage.dataplex.LineageScope;
import io.github.rejeb.dataform.language.lineage.dataplex.TimeRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Keeps the recorded lineage of the repository in the IDE's project cache, one entry per scope and
 * time range, so that it survives IDE restarts.
 */
public interface DataplexLineageCache {

    static DataplexLineageCache getInstance(@NotNull Project project) {
        return project.getService(DataplexLineageCache.class);
    }

    /**
     * @return the entry, decoded anew on every call, or {@code null} when there is none or it cannot be read
     */
    @Nullable LineageCacheEntry get(@NotNull LineageScope scope, @NotNull TimeRange range);

    /**
     * Keeps the entry in place of the one of the same scope and range.
     */
    void put(@NotNull LineageCacheEntry entry);

    /**
     * Forgets the entry of that scope and range.
     */
    void remove(@NotNull LineageScope scope, @NotNull TimeRange range);
}
