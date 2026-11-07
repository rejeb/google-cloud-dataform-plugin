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
package io.github.rejeb.dataform.language.gcp.pricing;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

/**
 * The tooltip of a cost: what the cost is, where its price comes from and what it leaves out.
 */
public final class CostTooltip {

    private static final String ESTIMATE = "Dry-run estimate of the query shown.";
    private static final String ESTIMATE_AT_MINIMUM =
            "Dry-run estimate of the query shown, at BigQuery's 10 MB minimum. A query reading several tables can be billed 10 MB per table.";
    private static final String TOTAL = "Dry-run estimate of the queries of this file.";
    private static final String TOTAL_AT_MINIMUM =
            "Dry-run estimate of the queries of this file, with BigQuery's 10 MB minimum for the smaller ones. A query reading several tables can be billed 10 MB per table.";
    private static final String JOB = "Cost of the bytes billed by this job.";
    private static final String RUN = "Total cost of the bytes billed by the actions of this run.";
    private static final String DISCOUNTS = "Excludes the monthly free TiB and contract discounts.";

    private CostTooltip() {
    }

    /**
     * The tooltip of a dry-run estimate; empty when there is no price note.
     */
    @NotNull
    public static String estimate(@NotNull String priceNote) {
        return of(ESTIMATE, priceNote);
    }

    /**
     * The tooltip of a dry-run estimate priced at BigQuery's minimum; empty when there is no price note.
     */
    @NotNull
    public static String estimateAtMinimum(@NotNull String priceNote) {
        return of(ESTIMATE_AT_MINIMUM, priceNote);
    }

    /**
     * The tooltip of the estimated total of a file; empty when there is no price note.
     */
    @NotNull
    public static String total(@NotNull String priceNote) {
        return of(TOTAL, priceNote);
    }

    /**
     * The tooltip of an estimated total with queries priced at BigQuery's minimum; empty when there is no price note.
     */
    @NotNull
    public static String totalAtMinimum(@NotNull String priceNote) {
        return of(TOTAL_AT_MINIMUM, priceNote);
    }

    /**
     * The tooltip of the actual cost of a job; empty when there is no price note.
     */
    @NotNull
    public static String job(@NotNull String priceNote) {
        return of(JOB, priceNote);
    }

    /**
     * The tooltip of the total cost of a workflow run; empty when there is no price note.
     */
    @NotNull
    public static String run(@NotNull String priceNote) {
        return of(RUN, priceNote);
    }

    @NotNull
    private static String of(@NotNull String head, @NotNull String priceNote) {
        if (priceNote.isBlank()) return "";
        return "<html>" + head + "<br>" + StringUtil.escapeXmlEntities(priceNote) + "<br>" + DISCOUNTS + "</html>";
    }
}
