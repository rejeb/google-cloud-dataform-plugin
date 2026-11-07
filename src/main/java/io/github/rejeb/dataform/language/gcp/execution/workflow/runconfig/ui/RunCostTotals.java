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
package io.github.rejeb.dataform.language.gcp.execution.workflow.runconfig.ui;

import io.github.rejeb.dataform.language.gcp.execution.workflow.model.BigQueryJobDetails;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.InvocationActionResult;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.InvocationActionState;
import io.github.rejeb.dataform.language.gcp.pricing.CostFormat;
import io.github.rejeb.dataform.language.gcp.pricing.CostText;
import io.github.rejeb.dataform.language.gcp.pricing.CostTooltip;
import io.github.rejeb.dataform.language.gcp.pricing.PriceQuote;
import io.github.rejeb.dataform.language.gcp.pricing.PriceSource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The bytes and cost of all the actions of a workflow run whose job details are known.
 *
 * @param bytesProcessed  sum of the bytes processed
 * @param bytesBilled     sum of the bytes billed
 * @param cost            sum of the cost of each action, priced at its own location
 * @param currency        currency of the cost
 * @param counted         actions whose job details are known
 * @param withJobs        actions that ran a BigQuery job
 * @param capacityActions counted actions priced by capacity, which add bytes but no cost
 * @param quote           the price shared by every priced action, null when none or mixed
 * @param mixedLocations  whether the priced actions ran in different locations
 * @param reliable        whether every price used can be trusted
 * @param complete        whether the run is over and every action with a job is counted
 */
public record RunCostTotals(long bytesProcessed,
                            long bytesBilled,
                            @NotNull BigDecimal cost,
                            @NotNull String currency,
                            int counted,
                            int withJobs,
                            int capacityActions,
                            @Nullable PriceQuote quote,
                            boolean mixedLocations,
                            boolean reliable,
                            boolean complete) {

    private static final String DEFAULT_CURRENCY = "USD";
    private static final String SEPARATOR = " · ";

    /**
     * Sums the actions of a run from the job details loaded so far.
     *
     * @param actions          the actions of the run
     * @param detailsByJobId   the job details loaded so far, by job id
     * @param quoteForLocation the price of a BigQuery location
     * @param runFinished      whether the run is over
     */
    @NotNull
    public static RunCostTotals of(@NotNull List<InvocationActionResult> actions,
                                   @NotNull Map<String, BigQueryJobDetails> detailsByJobId,
                                   @NotNull Function<String, PriceQuote> quoteForLocation,
                                   boolean runFinished) {
        long processed = 0;
        long billed = 0;
        BigDecimal cost = BigDecimal.ZERO;
        String currency = null;
        int counted = 0;
        int withJobs = 0;
        int capacity = 0;
        PriceQuote shared = null;
        boolean mixed = false;
        boolean reliable = true;
        for (InvocationActionResult action : actions) {
            if (action.jobId() == null || action.jobProject() == null) continue;
            withJobs++;
            BigQueryJobDetails details = detailsByJobId.get(action.jobId());
            if (details == null) continue;
            counted++;
            long actionBilled = details.bytesBilled() != null ? details.bytesBilled() : 0L;
            processed += details.bytesProcessed() != null ? details.bytesProcessed() : 0L;
            billed += actionBilled;
            PriceQuote quote = quoteForLocation.apply(details.location());
            if (currency == null) currency = quote.currency();
            if (details.reservationId() != null || quote.source() == PriceSource.CAPACITY
                    || quote.pricePerTib() == null) {
                capacity++;
                continue;
            }
            if (shared == null && !mixed) {
                shared = quote;
            } else if (shared != null && !shared.locationLabel().equals(quote.locationLabel())) {
                shared = null;
                mixed = true;
            }
            reliable &= quote.reliable();
            if (!InvocationActionState.FAILED.name().equals(details.status())) {
                cost = cost.add(CostFormat.costOf(actionBilled, quote.pricePerTib()));
            }
        }
        return new RunCostTotals(processed, billed, cost, currency != null ? currency : DEFAULT_CURRENCY,
                counted, withJobs, capacity, shared, mixed, reliable, runFinished && counted == withJobs);
    }

    /**
     * How many actions the totals cover, shown beside the total bytes processed.
     */
    @NotNull
    public String coverageNote() {
        if (withJobs == 0) return "";
        return complete
                ? "all " + actions(withJobs)
                : "so far, " + counted + " of " + actions(withJobs);
    }

    /**
     * The total cost and its note: where the prices come from, whether the run is still counting
     * and how many actions ran on reservations.
     */
    @NotNull
    public CostText costText() {
        if (counted == 0) {
            return new CostText("—", complete ? "" : "so far", true);
        }
        boolean priced = capacityActions < counted;
        String value = priced ? CostFormat.money(cost, currency) : "capacity pricing";
        List<String> notes = new ArrayList<>();
        if (!complete) notes.add("so far");
        if (priced) notes.add(mixedLocations || quote == null ? "mixed locations" : CostFormat.priceNote(quote, true));
        if (capacityActions > 0) notes.add(actions(capacityActions) + " on reservations");
        String note = String.join(SEPARATOR, notes);
        return new CostText(value, note, reliable, priced ? CostTooltip.run(note) : "");
    }

    @NotNull
    private static String actions(int count) {
        return count + (count == 1 ? " action" : " actions");
    }
}
