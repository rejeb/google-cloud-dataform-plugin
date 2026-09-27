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
package io.github.rejeb.dataform.language.diagnostics.sql.bigquery;

import com.intellij.openapi.project.Project;
import io.github.rejeb.dataform.language.schema.sql.DataformCteQueryBuilder;
import io.github.rejeb.dataform.language.schema.sql.DryRunErrorRegistry;
import io.github.rejeb.dataform.language.schema.sql.DryRunFailure;
import io.github.rejeb.dataform.language.schema.sql.DryRunQueryText;
import io.github.rejeb.dataform.language.schema.sql.model.ColumnInfo;
import io.github.rejeb.dataform.language.util.MappedText;

import java.util.List;
import java.util.Map;

/**
 * Builds dry-run failures the way the schema extraction records them, for the
 * {@code gold_customer_purchase_summary} action of the {@code DataformProjectFixture} graph.
 */
public final class BigQueryFailures {

    public static final String ACTION = "proj.ds.gold_customer_purchase_summary";
    public static final String PATH = "definitions/gold_customer_purchase_summary.sqlx";
    public static final String SQLX = "config { type: \"table\" }\n\nSELECT\n  order_idd,\n  1 AS order_amount\n"
            + "FROM ${ref(\"silver_orders\")}\n";
    public static final String COMPILED = "\n\nSELECT\n  order_idd,\n  1 AS order_amount\nFROM `proj.ds.silver_orders`\n";

    private static final Map<String, List<ColumnInfo>> STUBS = Map.of("proj.ds.silver_orders",
            List.of(new ColumnInfo("order_id", "INT64", "NULLABLE", null)));

    private BigQueryFailures() {
    }

    public static MappedText sent(Project project, String compiled) {
        return DryRunQueryText.withPreOperations(List.of(),
                DataformCteQueryBuilder.buildMappedDryRunQuery(compiled, STUBS, project));
    }

    public static String at(MappedText sent, String token) {
        int offset = sent.text().indexOf(token);
        String before = sent.text().substring(0, offset);
        int line = (int) before.chars().filter(c -> c == '\n').count() + 1;
        int column = offset - before.lastIndexOf('\n');
        return " at [" + line + ":" + column + "]";
    }

    public static void report(Project project, String message, MappedText sent) {
        DryRunErrorRegistry.getInstance(project).reportFailure(ACTION, new DryRunFailure(message, sent));
    }

    public static void clear(Project project) {
        DryRunErrorRegistry.getInstance(project).clear(ACTION);
    }
}
