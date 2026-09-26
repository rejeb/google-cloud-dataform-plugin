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
package io.github.rejeb.dataform.language.diagnostics.sql.hint;

import com.intellij.util.text.EditDistance;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Picks, among known names, the ones a misspelled name most likely meant.
 */
public final class NameSuggester {

    private static final int MAX_DISTANCE = 3;

    private NameSuggester() {
    }

    /**
     * The candidates close enough to the name to be a misspelling of it, closest first, at most
     * {@code limit} of them. A candidate much further than the closest one is left out, so that a
     * likely fix is not buried among unlikely ones. Case is ignored, as BigQuery ignores it for
     * names, and the name itself is never suggested.
     */
    public static @NotNull List<String> closest(@NotNull String name,
                                                @NotNull Collection<String> candidates,
                                                int limit) {
        int allowed = Math.max(1, Math.min(MAX_DISTANCE, name.length() / 3));
        Map<String, String> unique = new LinkedHashMap<>();
        for (String candidate : candidates) {
            unique.putIfAbsent(candidate.toLowerCase(Locale.ROOT), candidate);
        }
        record Scored(String candidate, int distance) {
        }
        List<Scored> close = unique.values().stream()
                .filter(candidate -> !candidate.equalsIgnoreCase(name))
                .map(candidate -> new Scored(candidate,
                        EditDistance.optimalAlignment(name, candidate, false, allowed + 1)))
                .filter(scored -> scored.distance() <= allowed)
                .sorted(Comparator.comparingInt(Scored::distance)
                        .thenComparing(Scored::candidate, String.CASE_INSENSITIVE_ORDER))
                .toList();
        if (close.isEmpty()) return List.of();
        int cutoff = close.getFirst().distance() + 1;
        return close.stream()
                .filter(scored -> scored.distance() <= cutoff)
                .limit(limit)
                .map(Scored::candidate)
                .toList();
    }
}
