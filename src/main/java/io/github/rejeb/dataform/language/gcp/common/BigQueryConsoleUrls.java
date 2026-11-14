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
package io.github.rejeb.dataform.language.gcp.common;

import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.NotNull;

/**
 * The pages of a BigQuery table in the Google Cloud console.
 */
public final class BigQueryConsoleUrls {

    private static final String CONSOLE = "https://console.cloud.google.com/bigquery";

    private BigQueryConsoleUrls() {
    }

    /**
     * The page of a table in the BigQuery explorer.
     *
     * @param fullName the table as {@code project.dataset.table}
     * @return the page of the table, or the BigQuery console when the name is not a table name
     */
    public static @NotNull String tableUrl(@NotNull String fullName) {
        int tableStart = fullName.lastIndexOf('.');
        int datasetStart = tableStart <= 0 ? -1 : fullName.lastIndexOf('.', tableStart - 1);
        if (datasetStart <= 0) return CONSOLE;
        String project = encode(fullName.substring(0, datasetStart));
        String dataset = encode(fullName.substring(datasetStart + 1, tableStart));
        String table = encode(fullName.substring(tableStart + 1));
        return CONSOLE + "?project=" + project + "&ws=!1m5!1m4!4m3!1s" + project + "!2s" + dataset + "!3s" + table;
    }

    /**
     * The page showing the lineage Knowledge Catalog recorded for a table. It is the page of the
     * table, whose Lineage tab shows it.
     */
    public static @NotNull String lineageUrl(@NotNull String fullName) {
        return tableUrl(fullName);
    }

    private static @NotNull String encode(@NotNull String value) {
        return URLUtil.encodeURIComponent(value);
    }
}
