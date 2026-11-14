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

import java.time.Duration;

/**
 * How far back the recorded lineage is shown.
 */
public enum TimeRange {
    LAST_30_DAYS("Last 30 days", Duration.ofDays(30)),
    LAST_7_DAYS("Last 7 days", Duration.ofDays(7)),
    LAST_24_HOURS("Last 24 hours", Duration.ofHours(24));

    private final String label;
    private final Duration length;

    TimeRange(@NotNull String label, @NotNull Duration length) {
        this.label = label;
        this.length = length;
    }

    /**
     * @return the length of the range
     */
    public @NotNull Duration length() {
        return length;
    }

    /**
     * @return the text of the range in the toolbar
     */
    public @NotNull String label() {
        return label;
    }
}
