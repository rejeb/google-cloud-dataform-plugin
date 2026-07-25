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

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.documentation.bigquery.BigQueryFunctionDoc;
import io.github.rejeb.dataform.language.schema.sql.model.ColumnInfo;

import java.util.List;

public class DataformDocumentationRendererTest extends BasePlatformTestCase {

    public void testRenderTableIncludesFullNameTypeAndColumns() {
        String html = DataformDocumentationRenderer.renderTable(
                "orders", "proj.ds.orders", "incremental", "definitions/orders.sqlx",
                "All customer orders",
                List.of(new ColumnInfo("order_id", "STRING", "REQUIRED", "Primary key"),
                        new ColumnInfo("amount", "NUMERIC", "NULLABLE", null)));

        assertTrue(html.contains("orders"));
        assertTrue(html.contains("proj.ds.orders"));
        assertTrue(html.contains("incremental"));
        assertTrue(html.contains("definitions/orders.sqlx"));
        assertTrue(html.contains("All customer orders"));
        assertTrue(html.contains("order_id"));
        assertTrue(html.contains("STRING"));
        assertTrue(html.contains("Primary key"));
        assertTrue(html.contains("amount"));
    }

    public void testRenderTableWithoutColumnsOmitsColumnSection() {
        String html = DataformDocumentationRenderer.renderTable(
                "orders", "proj.ds.orders", "table", null, null, List.of());
        assertTrue(html.contains("proj.ds.orders"));
        assertFalse(html.contains("Columns"));
    }

    public void testRenderTableToleratesNullMetadata() {
        String html = DataformDocumentationRenderer.renderTable(
                "orders", null, null, null, null, List.of());
        assertTrue(html.contains("orders"));
    }

    public void testRenderColumnIncludesTypeModeAndDescription() {
        String html = DataformDocumentationRenderer.renderColumn(
                new ColumnInfo("amount", "NUMERIC", "REPEATED", "Order total"), "orders");
        assertTrue(html.contains("amount"));
        assertTrue(html.contains("NUMERIC"));
        assertTrue(html.contains("REPEATED"));
        assertTrue(html.contains("Order total"));
        assertTrue(html.contains("orders"));
    }

    public void testRenderColumnRendersNestedSubFields() {
        ColumnInfo nested = new ColumnInfo("address", "RECORD", "NULLABLE", "Shipping address",
                List.of(new ColumnInfo("city", "STRING", "NULLABLE", "City name")));
        String html = DataformDocumentationRenderer.renderColumn(nested, "orders");
        assertTrue(html.contains("address"));
        assertTrue(html.contains("city"));
        assertTrue(html.contains("City name"));
    }

    public void testRenderFunctionIncludesSignatureAndLink() {
        String html = DataformDocumentationRenderer.renderFunction(new BigQueryFunctionDoc(
                "DATE_TRUNC", "date", List.of("DATE_TRUNC(date_expression, date_part)"),
                "Truncates a date value.", "DATE", "https://cloud.google.com/x"));
        assertTrue(html.contains("DATE_TRUNC(date_expression, date_part)"));
        assertTrue(html.contains("Truncates a date value."));
        assertTrue(html.contains("DATE"));
        assertTrue(html.contains("https://cloud.google.com/x"));
    }

    public void testDescriptionsAreHtmlEscaped() {
        String html = DataformDocumentationRenderer.renderColumn(
                new ColumnInfo("c", "STRING", "NULLABLE", "a <b> & c"), "t");
        assertFalse("raw description markup must not reach the popup", html.contains("a <b> & c"));
        assertTrue(html.contains("&lt;b&gt;"));
        assertTrue(html.contains("&amp;"));
    }
}
