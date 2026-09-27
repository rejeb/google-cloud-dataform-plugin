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
package io.github.rejeb.dataform.language.gcp.execution.bigquery;

import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.Field;
import com.google.cloud.bigquery.FieldValue;
import com.google.cloud.bigquery.FieldValueList;
import com.google.cloud.bigquery.Job;
import com.google.cloud.bigquery.Schema;
import com.google.cloud.bigquery.StandardSQLTypeName;
import com.google.cloud.bigquery.TableResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Drives {@link BigQueryPagedResult} against a job answering three pages of two rows each,
 * chained by the tokens {@code t1} and {@code t2}.
 */
public class BigQueryPagedResultTest {

    private static final Schema SCHEMA = Schema.of(Field.of("n", StandardSQLTypeName.INT64));

    private Job job;
    private final List<List<BigQuery.QueryResultsOption>> calls = new ArrayList<>();

    @BeforeEach
    public void setUp() throws Exception {
        job = mock(Job.class);
        Map<String, TableResult> pages = Map.of(
                "", page("t1", "0", "1"),
                "t1", page("t2", "2", "3"),
                "t2", page(null, "4", "5"));
        when(job.getQueryResults(any(BigQuery.QueryResultsOption[].class))).thenAnswer(invocation -> {
            List<BigQuery.QueryResultsOption> options = options(invocation.getArguments());
            calls.add(options);
            String token = "";
            for (BigQuery.QueryResultsOption option : options) {
                for (String candidate : List.of("t1", "t2")) {
                    if (option.equals(BigQuery.QueryResultsOption.pageToken(candidate))) {
                        token = candidate;
                    }
                }
            }
            return pages.get(token);
        });
    }

    @Test
    public void startsOnTheFirstPage() {
        BigQueryPagedResult result = new BigQueryPagedResult(job, SCHEMA, 6);
        result.setPageSize(2);
        assertEquals(List.of("0", "1"), values(result.loadFirstPage()));
        assertTrue(result.isFirstPage());
        assertFalse(result.isLastPage());
        assertEquals(0, result.getPageStart());
        assertEquals(2, result.getPageEnd());
        assertSame(SCHEMA, result.getSchema());
        assertEquals(6, result.getTotalRows());
        assertEquals(List.of(BigQuery.QueryResultsOption.pageSize(2)), calls.get(0));
    }

    @Test
    public void walksForwardWithTheNextPageToken() {
        BigQueryPagedResult result = new BigQueryPagedResult(job, SCHEMA, 6);
        result.setPageSize(2);
        result.loadFirstPage();
        assertEquals(List.of("2", "3"), values(result.loadNextPage()));
        assertEquals(1, result.getCurrentPage());
        assertEquals(List.of("4", "5"), values(result.loadNextPage()));
        assertTrue(result.isLastPage());
        assertEquals(6, result.getPageEnd());
        assertTrue(calls.get(1).contains(BigQuery.QueryResultsOption.pageToken("t1")));
        assertTrue(calls.get(2).contains(BigQuery.QueryResultsOption.pageToken("t2")));
    }

    @Test
    public void nextPageOnTheLastPageDoesNothing() throws Exception {
        BigQueryPagedResult result = new BigQueryPagedResult(job, SCHEMA, 2);
        result.setPageSize(2);
        result.loadFirstPage();
        assertTrue(result.isLastPage());
        assertTrue(result.loadNextPage().isEmpty());
        assertEquals(1, calls.size());
    }

    @Test
    public void previousPageReplaysTheCachedToken() {
        BigQueryPagedResult result = new BigQueryPagedResult(job, SCHEMA, 6);
        result.setPageSize(2);
        result.loadFirstPage();
        result.loadNextPage();
        result.loadNextPage();
        assertEquals(List.of("2", "3"), values(result.loadPreviousPage()));
        assertEquals(1, result.getCurrentPage());
        assertTrue(calls.get(3).contains(BigQuery.QueryResultsOption.pageToken("t1")));
        assertEquals(List.of("0", "1"), values(result.loadPreviousPage()));
        assertTrue(result.isFirstPage());
        assertEquals(List.of(BigQuery.QueryResultsOption.pageSize(2)), calls.get(4));
    }

    @Test
    public void previousPageOnTheFirstPageDoesNothing() {
        BigQueryPagedResult result = new BigQueryPagedResult(job, SCHEMA, 6);
        result.setPageSize(2);
        result.loadFirstPage();
        assertTrue(result.loadPreviousPage().isEmpty());
        assertEquals(1, calls.size());
    }

    @Test
    public void offsetJumpsThroughUnknownPages() {
        BigQueryPagedResult result = new BigQueryPagedResult(job, SCHEMA, 6);
        result.setPageSize(2);
        result.loadFirstPage();
        assertEquals(List.of("4", "5"), values(result.loadPageAtOffset(5)));
        assertEquals(2, result.getCurrentPage());
        assertEquals(3, calls.size());
    }

    @Test
    public void offsetReusesACachedToken() {
        BigQueryPagedResult result = new BigQueryPagedResult(job, SCHEMA, 6);
        result.setPageSize(2);
        result.loadFirstPage();
        result.loadNextPage();
        result.loadNextPage();
        calls.clear();
        assertEquals(List.of("2", "3"), values(result.loadPageAtOffset(2)));
        assertEquals(1, calls.size());
        assertTrue(calls.get(0).contains(BigQuery.QueryResultsOption.pageToken("t1")));
    }

    @Test
    public void changingThePageSizeRewindsToTheFirstPage() {
        BigQueryPagedResult result = new BigQueryPagedResult(job, SCHEMA, 6);
        assertEquals(BigQueryPagedResult.DEFAULT_PAGE_SIZE, result.getPageSize());
        result.setPageSize(2);
        result.loadFirstPage();
        result.loadNextPage();
        result.setPageSize(3);
        assertEquals(3, result.getPageSize());
        assertTrue(result.isFirstPage());
        assertEquals(0, result.getPageStart());
        assertEquals(3, result.getPageEnd());
    }

    @Test
    public void interruptedFetchYieldsNoRowsAndKeepsTheInterrupt() throws Exception {
        Job failing = mock(Job.class);
        when(failing.getQueryResults(any(BigQuery.QueryResultsOption[].class)))
                .thenThrow(new InterruptedException("stop"));
        BigQueryPagedResult result = new BigQueryPagedResult(failing, SCHEMA, 6);
        try {
            assertTrue(result.loadFirstPage().isEmpty());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    public void disposeCancelsTheJob() {
        BigQueryPagedResult result = new BigQueryPagedResult(job, SCHEMA, 6);
        verify(job, never()).cancel();
        result.dispose();
        verify(job).cancel();
    }

    private static TableResult page(String nextToken, String... values) {
        TableResult page = mock(TableResult.class);
        when(page.getNextPageToken()).thenReturn(nextToken);
        List<FieldValueList> rows = new ArrayList<>();
        for (String value : values) {
            rows.add(FieldValueList.of(List.of(FieldValue.of(FieldValue.Attribute.PRIMITIVE, value))));
        }
        when(page.getValues()).thenReturn(rows);
        return page;
    }

    private static List<BigQuery.QueryResultsOption> options(Object[] arguments) {
        List<BigQuery.QueryResultsOption> options = new ArrayList<>();
        for (Object argument : arguments) {
            if (argument instanceof BigQuery.QueryResultsOption option) {
                options.add(option);
            } else if (argument instanceof Object[] array) {
                options.addAll(options(array));
            }
        }
        return options;
    }

    private static List<String> values(List<FieldValueList> rows) {
        return rows.stream().map(row -> row.get(0).getStringValue()).toList();
    }
}
