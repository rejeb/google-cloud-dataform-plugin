/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.rejeb.dataform.language.gcp.bigquery;

import com.google.cloud.bigquery.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Encapsulates BigQuery pagination state for a completed job.
 * Allows fetching pages without re-executing the job.
 */
public class BigQueryPagedResult {

    public static final int   DEFAULT_PAGE_SIZE  = 100;

    private final Job                  job;
    private final Schema               schema;
    private final long                 totalRows;
    private final Map<Integer, String> pageTokenCache = new HashMap<>();

    private int    pageSize    = DEFAULT_PAGE_SIZE;
    private int    currentPage = 0;
    private String pageToken   = null;

    public BigQueryPagedResult(
            @NotNull Job job,
            @NotNull Schema schema,
            long totalRows
    ) {
        this.job       = job;
        this.schema    = schema;
        this.totalRows = totalRows;
    }

    public @NotNull List<FieldValueList> loadFirstPage() {
        currentPage = 0;
        pageToken   = null;
        return fetchPage();
    }

    public @NotNull List<FieldValueList> loadNextPage() {
        if (isLastPage()) return List.of();
        currentPage++;
        return fetchPage();
    }

    public @NotNull List<FieldValueList> loadPreviousPage() {
        if (isFirstPage()) return List.of();
        currentPage--;
        pageToken = pageTokenCache.getOrDefault(currentPage, null);
        return fetchPage();
    }

    public @NotNull List<FieldValueList> loadPageAtOffset(long rowOffset) {
        int targetPage = (int) (rowOffset / pageSize);
        if (pageTokenCache.containsKey(targetPage)) {
            currentPage = targetPage;
            pageToken   = pageTokenCache.get(targetPage);
            return fetchPage();
        }
        // token non en cache — rejoue depuis le dernier token connu
        int  lastKnown      = pageTokenCache.isEmpty() ? 0 : Collections.max(pageTokenCache.keySet());
        String lastToken    = lastKnown == 0 ? null : pageTokenCache.get(lastKnown);
        currentPage = lastKnown;
        pageToken   = lastToken;
        while (currentPage < targetPage) {
            fetchPage();
            currentPage++;
        }
        return fetchPage();
    }

    public boolean isFirstPage() { return currentPage == 0; }
    public boolean isLastPage()  { return getPageEnd() >= totalRows; }
    public int     getPageSize() { return pageSize; }
    public long    getTotalRows(){ return totalRows; }
    public int     getCurrentPage() { return currentPage; }
    public long    getPageStart()   { return (long) currentPage * pageSize; }
    public long    getPageEnd()     { return Math.min(getPageStart() + pageSize, totalRows); }
    public @NotNull Schema getSchema() { return schema; }

    public void setPageSize(int newPageSize) {
        this.pageSize    = newPageSize;
        this.currentPage = 0;
        this.pageToken   = null;
        this.pageTokenCache.clear();
    }

    private @NotNull List<FieldValueList> fetchPage() {
        List<BigQuery.QueryResultsOption> options = new ArrayList<>();
        options.add(BigQuery.QueryResultsOption.pageSize(pageSize));
        if (pageToken != null) {
            options.add(BigQuery.QueryResultsOption.pageToken(pageToken));
        }

        TableResult page;
        try {
            page = job.getQueryResults(options.toArray(new BigQuery.QueryResultsOption[0]));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return List.of();
        }

        String nextToken = page.getNextPageToken();
        pageTokenCache.put(currentPage + 1, nextToken);
        pageToken = nextToken;

        List<FieldValueList> rows = new ArrayList<>();
        for (FieldValueList row : page.getValues()) {
            rows.add(row);
        }
        return rows;
    }

    public void dispose() {
        job.cancel();
        pageTokenCache.clear();
    }

}
