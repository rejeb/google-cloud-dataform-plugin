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
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Which price list is in use.
 *
 * @param locationCount number of locations the list prices
 * @param updatedAt     when the list was downloaded, or the date of the built-in list
 * @param builtIn       whether the list is the one shipped with the plugin
 * @param lastError     why the last refresh failed, null when it succeeded or never ran
 */
public record PriceListStatus(int locationCount,
                              @Nullable Instant updatedAt,
                              boolean builtIn,
                              @Nullable String lastError) {

    private static final DateTimeFormatter UPDATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * The status line of the settings page, with the update time in the given zone.
     */
    @NotNull
    public String describe(@NotNull ZoneId zone) {
        StringBuilder text = new StringBuilder("Price list: ");
        if (builtIn || updatedAt == null) {
            text.append("built-in (").append(locationCount).append(" locations)");
        } else {
            text.append(locationCount).append(" locations, updated ")
                    .append(UPDATE_TIME.format(updatedAt.atZone(zone)));
        }
        if (lastError != null && !lastError.isBlank()) {
            text.append(" · refresh failed: ").append(lastError);
        }
        return text.toString();
    }
}
