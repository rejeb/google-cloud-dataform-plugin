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

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Groups the entities of a trace for display: BigQuery tables by project, everything else by the
 * system it lives in.
 */
public final class TraceGroups {

    private TraceGroups() {
    }

    /**
     * @param entries           the entities of a trace
     * @param repositoryProject the Google Cloud project of the repository, whose group comes first
     * @return the entries by group name, the other groups in alphabetical order; inside a group the
     * nearest entities first, then by name
     */
    public static @NotNull LinkedHashMap<String, List<TraceEntry>> group(@NotNull List<TraceEntry> entries,
                                                                         @NotNull String repositoryProject) {
        Map<String, List<TraceEntry>> byName = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (TraceEntry entry : entries) {
            byName.computeIfAbsent(groupOf(entry.fqn()), name -> new ArrayList<>()).add(entry);
        }
        Comparator<TraceEntry> order = Comparator.comparingInt(TraceEntry::level)
                .thenComparing(entry -> EntityNames.shortNameOf(entry.fqn()), String.CASE_INSENSITIVE_ORDER);
        LinkedHashMap<String, List<TraceEntry>> groups = new LinkedHashMap<>();
        List<TraceEntry> own = byName.remove(repositoryProject);
        if (own != null) groups.put(repositoryProject, own.stream().sorted(order).toList());
        byName.forEach((name, group) -> groups.put(name, group.stream().sorted(order).toList()));
        return groups;
    }

    private static @NotNull String groupOf(@NotNull String fqn) {
        String fullName = EntityNames.fullNameOf(fqn);
        if (fullName == null) return EntityNames.kindOf(fqn);
        int dot = fullName.indexOf('.');
        return dot < 0 ? fullName : fullName.substring(0, dot);
    }
}
