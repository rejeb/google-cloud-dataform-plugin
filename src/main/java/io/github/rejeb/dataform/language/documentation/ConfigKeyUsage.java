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
package io.github.rejeb.dataform.language.documentation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * The hand-written part of the config key documentation: how a key naming columns is filled in,
 * which the generated schema cannot tell since it only describes the shape of a value.
 */
public final class ConfigKeyUsage {

    private static final String COLUMNS = """
            Describes the columns of the action output. Each key names a column, each value is
            either its description or a descriptor object.

            columns: {
              customer_id: "Unique customer identifier",
              signup_date: "Day the customer signed up",
              address: {
                description: "Postal address of the customer",
                tags: ["pii"],
                bigqueryPolicyTags: ["projects/p/locations/eu/taxonomies/t/policyTags/1"],
                columns: {
                  city: "City of the postal address"
                }
              }
            }

            The descriptor object accepts description, tags, bigqueryPolicyTags, and columns to
            describe the fields of a RECORD column.""";

    private static final String PARTITION_BY = """
            Names the column the table is partitioned by, or a SQL expression over one.

            partitionBy: "event_date"
            partitionBy: "DATE(event_timestamp)"

            The column must be a date, timestamp, datetime or integer column.""";

    private static final String CLUSTER_BY = """
            Names the columns the table partitions are clustered by, most selective first.

            clusterBy: ["country", "customer_id"]

            BigQuery accepts at most four clustering columns.""";

    private static final String UNIQUE_KEY = """
            Names the columns the incremental merge matches rows on.

            uniqueKey: ["customer_id", "event_date"]

            Rows of the increment whose key already exists are updated instead of inserted.""";

    private static final String ASSERTION_UNIQUE_KEY = """
            Names the columns whose combination must be unique across the output.

            uniqueKey: ["customer_id"]""";

    private static final String ASSERTION_UNIQUE_KEYS = """
            Names several combinations of columns, each of which must be unique across the output.

            uniqueKeys: [["customer_id"], ["email", "signup_date"]]""";

    private static final String NON_NULL = """
            Names the columns that must never be NULL in the output.

            nonNull: ["customer_id", "signup_date"]""";

    private static final String FIELD = """
            Names the column the table is partitioned by.

            partitionBy: {
              field: "event_timestamp",
              dataType: "timestamp",
              granularity: "day"
            }""";

    private static final Map<String, String> USAGE = Map.of(
            "columns", COLUMNS,
            "partitionBy", PARTITION_BY,
            "clusterBy", CLUSTER_BY,
            "uniqueKey", UNIQUE_KEY,
            "uniqueKeys", ASSERTION_UNIQUE_KEYS,
            "nonNull", NON_NULL,
            "field", FIELD);

    private ConfigKeyUsage() {
    }

    /**
     * The usage of the given config key, {@code null} when it needs none beyond the description the
     * schema already carries.
     */
    @Nullable
    public static String of(@NotNull String key, boolean underAssertions) {
        if (underAssertions && "uniqueKey".equals(key)) {
            return ASSERTION_UNIQUE_KEY;
        }
        return USAGE.get(key);
    }

    /**
     * The usage shown on a column entry of a {@code columns} map whose column is unknown to the
     * extracted schema.
     */
    @NotNull
    public static String columnEntry() {
        return COLUMNS;
    }
}
