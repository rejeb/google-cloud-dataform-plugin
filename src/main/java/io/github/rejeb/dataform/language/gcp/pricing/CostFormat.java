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

import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Currency;
import java.util.Locale;

/**
 * Computes and formats BigQuery costs.
 */
public final class CostFormat {

    private static final BigDecimal BYTES_PER_TIB = BigDecimal.valueOf(1L << 40);
    private static final BigDecimal SMALLEST = new BigDecimal("0.0001");
    private static final DateTimeFormatter LIST_DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);
    private static final String SEPARATOR = " · ";

    private CostFormat() {
    }

    /**
     * Returns the cost of the given bytes at the given price per TiB.
     */
    @NotNull
    public static BigDecimal costOf(long bytes, @NotNull BigDecimal pricePerTib) {
        return BigDecimal.valueOf(bytes).multiply(pricePerTib).divide(BYTES_PER_TIB, 10, RoundingMode.HALF_UP);
    }

    /**
     * Formats an amount: four decimals below one unit, two from one unit, and a floor for amounts
     * too small to show.
     */
    @NotNull
    public static String money(@NotNull BigDecimal amount, @NotNull String currency) {
        String symbol = symbolOf(currency);
        if (amount.signum() == 0) return symbol + "0.00";
        if (amount.compareTo(SMALLEST) < 0) return "< " + symbol + SMALLEST.toPlainString();
        int scale = amount.compareTo(BigDecimal.ONE) < 0 ? 4 : 2;
        return symbol + amount.setScale(scale, RoundingMode.HALF_UP).toPlainString();
    }

    /**
     * Describes the price of a quote: its location, its price per TiB and where it comes from.
     *
     * @param billed whether the price applies to billed bytes
     */
    @NotNull
    public static String priceNote(@NotNull PriceQuote quote, boolean billed) {
        if (quote.source() == PriceSource.CAPACITY || quote.pricePerTib() == null) return "";
        String perTib = symbolOf(quote.currency())
                + quote.pricePerTib().setScale(2, RoundingMode.HALF_UP).toPlainString()
                + " per TiB" + (billed ? " billed" : "");
        return switch (quote.source()) {
            case FIXED -> "Fixed price" + SEPARATOR + perTib;
            case OLD_LIST -> quote.locationLabel() + SEPARATOR + perTib + SEPARATOR + "list price"
                    + (quote.listDate() != null ? " of " + LIST_DATE.format(quote.listDate()) : "");
            case BUILT_IN -> quote.locationLabel() + SEPARATOR + perTib + SEPARATOR
                    + "built-in list, may be outdated";
            default -> quote.locationLabel() + SEPARATOR + perTib + SEPARATOR + "list price";
        };
    }

    @NotNull
    private static String symbolOf(@NotNull String currency) {
        try {
            return Currency.getInstance(currency).getSymbol(Locale.ENGLISH);
        } catch (IllegalArgumentException e) {
            return currency + " ";
        }
    }
}
