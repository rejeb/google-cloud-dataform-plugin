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

import io.github.rejeb.dataform.language.schema.sql.model.ColumnInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One of the expressions BigQuery accepts to partition a table by, with the column types it takes
 * and the values its placeholders offer.
 *
 * <p>The list mirrors the {@code partition_expression} of a {@code CREATE TABLE}, which admits
 * these forms and no others: a bare date column, {@code DATE} of a timestamp or datetime column,
 * {@code DATE_TRUNC}, {@code DATETIME_TRUNC} and {@code TIMESTAMP_TRUNC} of a column of the matching
 * type, {@code RANGE_BUCKET} of an integer column, and, for ingestion time, {@code _PARTITIONDATE}
 * or {@code TIMESTAMP_TRUNC} of {@code _PARTITIONTIME}. A timestamp or datetime column named on its
 * own is not one of them, which is why it is never offered bare.</p>
 *
 * <p>The template text carries the variable names as {@code $name$}, the notation the platform
 * template engine reads, {@link #COLUMN} naming the column stop and {@link #GRANULARITY} the
 * truncation unit.</p>
 *
 * @param label         what the completion popup shows for the form
 * @param template      the expression written on pick, placeholders included
 * @param columnTypes   the column types the expression takes, empty when it takes no column
 * @param granularities the truncation units the granularity stop offers, empty when it has none
 * @param boundaries    the call the expression takes as its second argument, empty when it takes none
 * @param pseudoColumns the ingestion time pseudo-columns the expression takes besides real ones
 */
public record PartitionForm(@NotNull String label,
                            @NotNull String template,
                            @NotNull Set<String> columnTypes,
                            @NotNull List<String> granularities,
                            @NotNull String boundaries,
                            @NotNull List<String> pseudoColumns) {

    public static final String COLUMN = "col";
    public static final String GRANULARITY = "granularity";

    /** The pseudo-column carrying the ingestion time of a row. */
    public static final String PARTITION_TIME = "_PARTITIONTIME";

    /**
     * The only second argument BigQuery accepts for an integer range partition, its bounds being
     * constants.
     */
    private static final String GENERATE_ARRAY = "GENERATE_ARRAY($start$, $end$, $interval$)";

    private static final Map<String, String> NUMERIC_DEFAULTS =
            Map.of("start", "0", "end", "100", "interval", "10");

    private static final List<String> TIME_GRANULARITIES = List.of("HOUR", "DAY", "MONTH", "YEAR");
    private static final List<String> DATE_GRANULARITIES = List.of("MONTH", "YEAR");

    /**
     * The column types a column named on its own can have. Only a date column partitions a table
     * that way: a timestamp or datetime column has to be truncated, and an integer one bucketed.
     */
    public static final Set<String> BARE_COLUMN_TYPES = Set.of(PartitionColumns.DATE);

    private static final List<PartitionForm> FORMS = List.of(
            new PartitionForm("DATE(...)",
                    "DATE($col$)",
                    Set.of(PartitionColumns.TIMESTAMP, PartitionColumns.DATETIME),
                    List.of(), "", List.of()),
            new PartitionForm("DATE_TRUNC(..., MONTH)",
                    "DATE_TRUNC($col$, $granularity$)",
                    Set.of(PartitionColumns.DATE),
                    DATE_GRANULARITIES, "", List.of()),
            new PartitionForm("DATETIME_TRUNC(..., DAY)",
                    "DATETIME_TRUNC($col$, $granularity$)",
                    Set.of(PartitionColumns.DATETIME),
                    TIME_GRANULARITIES, "", List.of()),
            new PartitionForm("TIMESTAMP_TRUNC(..., DAY)",
                    "TIMESTAMP_TRUNC($col$, $granularity$)",
                    Set.of(PartitionColumns.TIMESTAMP),
                    TIME_GRANULARITIES, "", List.of(PARTITION_TIME)),
            new PartitionForm("RANGE_BUCKET(..., GENERATE_ARRAY(0, 100, 10))",
                    "RANGE_BUCKET($col$, " + GENERATE_ARRAY + ")",
                    Set.of(PartitionColumns.INTEGER),
                    List.of(), GENERATE_ARRAY, List.of()),
            new PartitionForm("_PARTITIONDATE",
                    "_PARTITIONDATE",
                    Set.of(), List.of(), "", List.of()));

    /**
     * Returns the forms the given columns justify: one taking no column is always offered, one
     * taking a column only when a column it accepts exists. Columns being unknown, which happens
     * before the project has been compiled, every form is offered rather than none.
     */
    @NotNull
    public static List<PartitionForm> availableFor(@NotNull List<ColumnInfo> columns) {
        if (columns.isEmpty()) {
            return FORMS;
        }
        return FORMS.stream()
                .filter(form -> !form.takesColumn() || !form.argumentsIn(columns).isEmpty())
                .toList();
    }

    /**
     * Returns the real columns of the action the given form takes, the pseudo-columns left out. A
     * form has one to offer even when the schema names no column of its type only through them.
     */
    @NotNull
    public static List<ColumnInfo> namedColumnsOf(@NotNull PartitionForm form,
                                                  @NotNull List<ColumnInfo> columns) {
        return PartitionColumns.ofTypes(columns, form.columnTypes());
    }

    /**
     * Returns the form the given function name introduces, ignoring case as SQL does, null when no
     * partitioning form is written with it.
     */
    @Nullable
    public static PartitionForm byFunction(@NotNull String name) {
        return FORMS.stream()
                .filter(PartitionForm::takesColumn)
                .filter(form -> form.functionName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }

    /**
     * The value each numeric placeholder starts on, which is also what the popup shows for it.
     */
    @NotNull
    public static Map<String, String> numericDefaults() {
        return NUMERIC_DEFAULTS;
    }

    /**
     * The names the column of this form can take among the given ones, the ingestion time
     * pseudo-columns it also accepts included.
     */
    @NotNull
    public List<String> argumentsIn(@NotNull List<ColumnInfo> columns) {
        List<String> names = new ArrayList<>(
                PartitionColumns.ofTypes(columns, columnTypes).stream().map(ColumnInfo::name).toList());
        names.addAll(pseudoColumns);
        return names;
    }

    /**
     * The function the form is written with, empty when it is written without a call.
     */
    @NotNull
    public String functionName() {
        int open = template.indexOf('(');
        return open < 0 ? "" : template.substring(0, open);
    }

    /**
     * Tells whether the form partitions by a column the user has to name.
     */
    public boolean takesColumn() {
        return template.contains("$" + COLUMN + "$");
    }

    /**
     * Tells whether picking this form leaves placeholders to fill rather than a finished expression.
     */
    public boolean hasPlaceholders() {
        return template.indexOf('$') >= 0;
    }

    /**
     * The second argument of the form as the popup shows it, its placeholders replaced by the
     * values they start on, empty when the form takes no second argument.
     */
    @NotNull
    public String boundariesLabel() {
        return fillValues(boundaries);
    }

    /**
     * The form written on the given column, with its remaining placeholders left to fill.
     */
    @NotNull
    public String templateOn(@NotNull String column) {
        return template.replace("$" + COLUMN + "$", column);
    }

    /**
     * The form written on the given column as the popup shows it, every placeholder replaced by the
     * value it starts on.
     */
    @NotNull
    public String labelOn(@NotNull String column) {
        return writtenOn(column, Map.of());
    }

    /**
     * The form written on the given column, the named placeholders taking the given values and the
     * remaining ones the value they start on.
     */
    @NotNull
    public String writtenOn(@NotNull String column, @NotNull Map<String, String> values) {
        String written = templateOn(column);
        for (Map.Entry<String, String> value : values.entrySet()) {
            written = written.replace("$" + value.getKey() + "$", value.getValue());
        }
        return fillValues(written);
    }

    /**
     * The default the granularity stop starts on, {@code DAY} when the form offers it.
     */
    @NotNull
    public String defaultGranularity() {
        return granularities.contains("DAY") ? "DAY" : granularities.getFirst();
    }

    @NotNull
    private String fillValues(@NotNull String text) {
        String filled = granularities.isEmpty()
                ? text
                : text.replace("$" + GRANULARITY + "$", defaultGranularity());
        for (Map.Entry<String, String> value : NUMERIC_DEFAULTS.entrySet()) {
            filled = filled.replace("$" + value.getKey() + "$", value.getValue());
        }
        return filled;
    }
}
