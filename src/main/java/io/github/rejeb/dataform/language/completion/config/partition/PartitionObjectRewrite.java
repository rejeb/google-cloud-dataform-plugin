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
package io.github.rejeb.dataform.language.completion.config.partition;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Turns the object some projects write {@code partitionBy} as into the string expression Dataform
 * takes.
 *
 * <p>Dataform declares {@code partition_by} as a string in every action of its configuration, so an
 * object describing the column, its type and a granularity, the shape other transformation tools
 * use, has to be written out as the matching BigQuery partitioning expression.</p>
 *
 * @param field       the column the table is partitioned by
 * @param dataType    its type, null when the object leaves it out
 * @param granularity the partition width, null when the object leaves it out
 * @param start       the first bucket of a range partition, null when left out
 * @param end         the last bucket of a range partition, null when left out
 * @param interval    the width of a range bucket, null when left out
 */
public record PartitionObjectRewrite(@NotNull String field,
                                     @Nullable String dataType,
                                     @Nullable String granularity,
                                     @Nullable String start,
                                     @Nullable String end,
                                     @Nullable String interval) {

    /**
     * The expression the object stands for, empty when the type of the column is neither given nor
     * known, which leaves nothing to write.
     */
    @NotNull
    public Optional<String> expression() {
        if (field.isBlank() || dataType == null) {
            return Optional.empty();
        }
        String type = PartitionColumns.normalize(dataType);
        if (PartitionColumns.DATE.equals(type)) {
            return Optional.of(dateExpression());
        }
        PartitionForm form = formOf(type);
        return form == null ? Optional.empty() : Optional.of(form.writtenOn(field, values(form)));
    }

    /**
     * A date column partitions a table daily under its own name, so only a coarser granularity is
     * written as a truncation. BigQuery has no hourly partition on a date column, which leaves the
     * daily one as the closest it can be given.
     */
    @NotNull
    private String dateExpression() {
        PartitionForm truncation = PartitionForm.byFunction("DATE_TRUNC");
        String unit = unit();
        return truncation != null && truncation.granularities().contains(unit)
                ? truncation.writtenOn(field, Map.of(PartitionForm.GRANULARITY, unit))
                : field;
    }

    @Nullable
    private PartitionForm formOf(@NotNull String type) {
        return switch (type) {
            case PartitionColumns.TIMESTAMP -> PartitionForm.byFunction("TIMESTAMP_TRUNC");
            case PartitionColumns.DATETIME -> PartitionForm.byFunction("DATETIME_TRUNC");
            case PartitionColumns.INTEGER -> PartitionForm.byFunction("RANGE_BUCKET");
            default -> null;
        };
    }

    /**
     * The placeholder values the object carries, the ones it leaves out being filled by the form
     * with the value it starts on. A granularity the form does not admit is left out too.
     */
    @NotNull
    private Map<String, String> values(@NotNull PartitionForm form) {
        Map<String, String> values = new LinkedHashMap<>();
        if (form.granularities().contains(unit())) {
            values.put(PartitionForm.GRANULARITY, unit());
        }
        put(values, "start", start);
        put(values, "end", end);
        put(values, "interval", interval);
        return values;
    }

    private static void put(@NotNull Map<String, String> values,
                            @NotNull String name,
                            @Nullable String value) {
        if (value != null && !value.isBlank()) {
            values.put(name, value.trim());
        }
    }

    @NotNull
    private String unit() {
        return granularity == null ? "" : granularity.trim().toUpperCase();
    }
}
