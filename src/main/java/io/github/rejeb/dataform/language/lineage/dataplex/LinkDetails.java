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
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;

/**
 * What is recorded about the flow of data from one table to another.
 *
 * @param firstSeen when the flow was first recorded, {@code null} when it is not loaded
 * @param lastSeen  when the flow was last recorded, {@code null} when it is not loaded
 * @param processes what moved the data
 * @param runs      the runs of those processes, newest first
 */
public record LinkDetails(@Nullable Instant firstSeen, @Nullable Instant lastSeen,
                          @NotNull List<RecordedProcess> processes, @NotNull List<RecordedRun> runs) {

    /** The details of a flow that is not loaded. */
    public static final LinkDetails NONE = new LinkDetails(null, null, List.of(), List.of());

    public LinkDetails {
        processes = List.copyOf(processes);
        runs = List.copyOf(runs);
    }
}
