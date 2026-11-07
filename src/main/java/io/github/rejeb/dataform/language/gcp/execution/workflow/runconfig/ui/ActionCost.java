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
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.InvocationActionState;
import io.github.rejeb.dataform.language.gcp.pricing.CostFormat;
import io.github.rejeb.dataform.language.gcp.pricing.CostText;
import io.github.rejeb.dataform.language.gcp.pricing.CostTooltip;
import io.github.rejeb.dataform.language.gcp.pricing.PriceQuote;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;

/**
 * The cost of the BigQuery job of a workflow action.
 */
public final class ActionCost {

    private static final String NOT_BILLED = "failed queries are not billed";
    private static final String UNKNOWN = "—";
    private static final String NOT_BILLED_PER_BYTE = "Not billed per byte";
    private static final String RESERVATION_NOTE = "capacity pricing · reservation ";

    private ActionCost() {
    }

    /**
     * The actual cost of the job's billed bytes; a failed job costs nothing and a job on a
     * reservation is not billed per byte.
     */
    @NotNull
    public static CostText of(@NotNull BigQueryJobDetails details, @NotNull PriceQuote quote) {
        if (details.reservationId() != null) {
            return new CostText(NOT_BILLED_PER_BYTE, RESERVATION_NOTE + details.reservationId(), true);
        }
        if (InvocationActionState.FAILED.name().equals(details.status())) {
            return new CostText(CostFormat.money(BigDecimal.ZERO, quote.currency()), NOT_BILLED, true,
                    CostTooltip.job(CostFormat.priceNote(quote, true)));
        }
        if (details.bytesBilled() == null) {
            return new CostText(UNKNOWN, "", true);
        }
        return CostText.actual(details.bytesBilled(), quote);
    }
}
