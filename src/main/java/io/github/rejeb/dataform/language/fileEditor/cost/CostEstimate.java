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
package io.github.rejeb.dataform.language.fileEditor.cost;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The result of a cost dry-run of one query.
 *
 * @param bytesProcessed     bytes the query would process, null when the dry-run failed
 * @param location           location the dry-run job ran in, null when unknown
 * @param error              BigQuery's message when the dry-run failed, null otherwise
 * @param minimumBilledBytes the least BigQuery bills for the query, 0 when it reads no table
 */
public record CostEstimate(@Nullable Long bytesProcessed, @Nullable String location, @Nullable String error,
                           long minimumBilledBytes) {

    /**
     * The 10 MiB BigQuery bills at least for a query reading a table.
     */
    public static final long MINIMUM_BILLED_BYTES = 10L * 1024 * 1024;

    /**
     * A dry-run that reported the bytes the query would process, with no minimum to bill.
     */
    @NotNull
    public static CostEstimate success(long bytesProcessed, @Nullable String location) {
        return success(bytesProcessed, location, 0);
    }

    /**
     * A dry-run that reported the bytes the query would process and the least BigQuery bills for it.
     */
    @NotNull
    public static CostEstimate success(long bytesProcessed, @Nullable String location, long minimumBilledBytes) {
        return new CostEstimate(bytesProcessed, location, null, minimumBilledBytes);
    }

    /**
     * A dry-run BigQuery refused, with its message.
     */
    @NotNull
    public static CostEstimate failure(@NotNull String message) {
        return new CostEstimate(null, null, message, 0);
    }

    /**
     * The bytes the cost of the query is computed from: those it would process, or the minimum billed
     * when that is more.
     */
    public long billedBytes() {
        return Math.max(bytesProcessed == null ? 0 : bytesProcessed, minimumBilledBytes);
    }

    /**
     * Tells whether the cost of the query comes from the minimum billed rather than from its bytes.
     */
    public boolean minimumApplies() {
        return !failed() && billedBytes() > (bytesProcessed == null ? 0 : bytesProcessed);
    }

    /**
     * Tells whether the dry-run failed.
     */
    public boolean failed() {
        return error != null;
    }
}
