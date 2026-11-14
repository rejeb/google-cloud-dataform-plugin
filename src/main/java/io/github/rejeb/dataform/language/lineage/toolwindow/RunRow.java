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
package io.github.rejeb.dataform.language.lineage.toolwindow;

import io.github.rejeb.dataform.language.lineage.dataplex.EntityNames;
import io.github.rejeb.dataform.language.lineage.dataplex.ProcessRun;
import io.github.rejeb.dataform.language.lineage.dataplex.RecordedRun;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.stream.Collectors;

/**
 * A row of the Runs tab.
 *
 * @param state    how the run ended, or that it is still going
 * @param started  when the run started
 * @param duration how long the run took, {@code null} while it is going
 * @param process  the name of the process that ran
 * @param moved    the data the run moved, such as "stg_orders, stg_customers → orders_enriched"
 * @param jobId    the id of the latest BigQuery job of the process, {@code null} when it reports none
 */
public record RunRow(@NotNull RecordedRun.State state, @NotNull Instant started, @Nullable Duration duration,
                     @NotNull String process, @NotNull String moved, @Nullable String jobId) {

    /**
     * @return the row of a recorded run
     */
    public static @NotNull RunRow of(@NotNull ProcessRun run) {
        Instant end = run.run().end();
        String sources = run.sourceFqns().stream().map(EntityNames::shortNameOf).collect(Collectors.joining(", "));
        return new RunRow(run.run().state(), run.run().start(),
                end == null ? null : Duration.between(run.run().start(), end), run.process().displayName(),
                sources + " → " + EntityNames.shortNameOf(run.targetFqn()), run.process().latestJobId());
    }
}
