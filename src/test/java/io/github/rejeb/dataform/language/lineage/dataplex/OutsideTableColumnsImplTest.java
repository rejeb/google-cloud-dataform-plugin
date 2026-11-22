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

import com.google.cloud.bigquery.BigQueryException;
import com.google.cloud.bigquery.Field;
import com.google.cloud.bigquery.Schema;
import com.google.cloud.bigquery.StandardSQLTypeName;
import com.google.cloud.bigquery.TableId;
import io.github.rejeb.dataform.language.columns.model.ColumnRef;
import io.github.rejeb.dataform.language.lineage.model.LineageModel;
import io.github.rejeb.dataform.language.util.concurrent.ParallelCalls;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutsideTableColumnsImplTest {

    private final Map<TableId, Schema> schemas = new LinkedHashMap<>();
    private final Map<TableId, RuntimeException> failures = new LinkedHashMap<>();
    private final List<TableId> asked = new ArrayList<>();
    private final List<String> billed = new ArrayList<>();

    private OutsideTableColumns reader() {
        return new OutsideTableColumnsImpl((projectId, table) -> {
            billed.add(projectId);
            asked.add(table);
            RuntimeException failure = failures.get(table);
            if (failure != null) throw failure;
            return schemas.get(table);
        });
    }

    @Test
    void theTopLevelFieldsOfATableAreItsColumnsInSchemaOrder() {
        schemas.put(TableId.of("other", "raw", "orders"), Schema.of(
                Field.of("id", StandardSQLTypeName.INT64),
                Field.newBuilder("tags", StandardSQLTypeName.STRING).setMode(Field.Mode.REPEATED).build(),
                Field.of("customer", StandardSQLTypeName.STRUCT, Field.of("name", StandardSQLTypeName.STRING))));

        OutsideTableColumns.Result result = reader().load("p", List.of("other.raw.orders"));

        assertEquals(List.of(new ColumnRef("other.raw.orders", "id"), new ColumnRef("other.raw.orders", "tags"),
                new ColumnRef("other.raw.orders", "customer")), result.columns().get("other.raw.orders"));
        assertEquals("INT64", result.types().get(LineageModel.columnTypeKey("other.raw.orders", "id")));
        assertEquals("ARRAY<STRING>", result.types().get(LineageModel.columnTypeKey("other.raw.orders", "tags")));
        assertEquals("STRUCT", result.types().get(LineageModel.columnTypeKey("other.raw.orders", "customer")));
        assertTrue(result.unreadable().isEmpty());
        assertEquals(List.of(TableId.of("other", "raw", "orders")), asked);
        assertEquals(List.of("p"), billed);
    }

    @Test
    void aTableThatIsDeniedOrMissingIsUnreadable() {
        failures.put(TableId.of("other", "raw", "denied"), new BigQueryException(403, "Access Denied"));
        failures.put(TableId.of("other", "raw", "gone"), new BigQueryException(404, "Not found"));
        schemas.put(TableId.of("other", "raw", "orders"), Schema.of(Field.of("id", StandardSQLTypeName.INT64)));

        OutsideTableColumns.Result result = reader().load("p",
                List.of("other.raw.denied", "other.raw.gone", "other.raw.absent", "other.raw.orders"));

        assertEquals(Set.of("other.raw.denied", "other.raw.gone", "other.raw.absent"), result.unreadable());
        assertEquals(Set.of("other.raw.orders"), result.columns().keySet());
    }

    @Test
    void anyOtherFailureOnATableLeavesItUnreadableAndReadsTheOthers() {
        failures.put(TableId.of("other", "raw", "broken"), new IllegalStateException("no credentials"));
        schemas.put(TableId.of("other", "raw", "orders"), Schema.of(Field.of("id", StandardSQLTypeName.INT64)));

        OutsideTableColumns.Result result = reader().load("p", List.of("other.raw.broken", "other.raw.orders"));

        assertEquals(Set.of("other.raw.broken"), result.unreadable());
        assertEquals(Set.of("other.raw.orders"), result.columns().keySet());
    }

    @Test
    void aNameThatIsNotABigQueryTableIsLeftOutWithoutACall() {
        OutsideTableColumns.Result result = reader().load("p", List.of("gcs:bucket.exports", "orders"));

        assertTrue(result.columns().isEmpty());
        assertTrue(result.unreadable().isEmpty());
        assertTrue(asked.isEmpty());
    }

    @Test
    void aProjectWithADomainKeepsItsDots() {
        schemas.put(TableId.of("example.com:prod", "raw", "orders"), Schema.of(Field.of("id", StandardSQLTypeName.INT64)));

        OutsideTableColumns.Result result = reader().load("p", List.of("example.com:prod.raw.orders"));

        assertEquals(Set.of("example.com:prod.raw.orders"), result.columns().keySet());
    }

    @Test
    void theSameTableIsReadOnce() {
        schemas.put(TableId.of("other", "raw", "orders"), Schema.of(Field.of("id", StandardSQLTypeName.INT64)));

        reader().load("p", List.of("other.raw.orders", "other.raw.orders"));

        assertEquals(1, asked.size());
    }

    @Test
    void tablesAreReadInParallelAndKeptInTheGivenOrder() {
        java.util.concurrent.atomic.AtomicInteger running = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger highest = new java.util.concurrent.atomic.AtomicInteger();
        OutsideTableColumns reader = new OutsideTableColumnsImpl((projectId, table) -> {
            highest.accumulateAndGet(running.incrementAndGet(), Math::max);
            try {
                Thread.sleep(table.getTable().equals("first") ? 90 : 20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                running.decrementAndGet();
            }
            return Schema.of(Field.of("id_" + table.getTable(), StandardSQLTypeName.INT64));
        }, ParallelCalls.shared());

        OutsideTableColumns.Result result = reader.load("p", List.of("o.r.first", "o.r.second", "o.r.third"));

        assertEquals(List.of("o.r.first", "o.r.second", "o.r.third"), List.copyOf(result.columns().keySet()));
        assertTrue(highest.get() >= 2, "highest " + highest.get());
    }
}
