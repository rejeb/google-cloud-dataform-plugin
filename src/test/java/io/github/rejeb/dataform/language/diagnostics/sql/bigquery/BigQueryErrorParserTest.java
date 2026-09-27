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

import org.junit.jupiter.api.Test;

import static io.github.rejeb.dataform.language.diagnostics.sql.bigquery.BigQueryErrorKind.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BigQueryErrorParserTest {

    private static void assertParsed(String message, BigQueryErrorKind kind, String subject, String qualifier) {
        BigQueryError error = BigQueryErrorParser.parse(message);
        assertEquals(kind, error.kind(), message);
        assertEquals(subject, error.subject(), message);
        assertEquals(qualifier, error.qualifier(), message);
    }

    @Test
    public void anUnrecognizedNameCarriesItsNameSuggestionAndPosition() {
        BigQueryError error = BigQueryErrorParser.parse(
                "Unrecognized name: custmer_id; Did you mean customer_id? at [4:3]");

        assertEquals(UNRECOGNIZED_NAME, error.kind());
        assertEquals("Unrecognized name: custmer_id", error.message());
        assertEquals("custmer_id", error.subject());
        assertEquals("customer_id", error.suggestion());
        assertEquals(4, error.line());
        assertEquals(3, error.column());
        assertTrue(error.hasPosition());
    }

    @Test
    public void namesAndFunctionsAreTakenApart() {
        assertParsed("Name order_tss not found inside o at [5:5]", NAME_NOT_FOUND_INSIDE, "order_tss", "o");
        assertParsed("Field name cty does not exist in STRUCT<city STRING, zip STRING> at [2:18]",
                FIELD_NOT_FOUND, "cty", "STRUCT<city STRING, zip STRING>");
        assertParsed("Function not found: SUMM; Did you mean sum? at [1:8]", FUNCTION_NOT_FOUND, "SUMM", null);
        assertEquals("sum", BigQueryErrorParser.parse("Function not found: SUMM; Did you mean sum? at [1:8]").suggestion());
        assertParsed("Unrecognized name: `order id` at [1:8]", UNRECOGNIZED_NAME, "order id", null);
    }

    @Test
    public void tablesAreTakenApartWithoutPosition() {
        BigQueryError missing = BigQueryErrorParser.parse(
                "Not found: Table my-proj:ds.orderz was not found in location US");

        assertEquals(TABLE_NOT_FOUND, missing.kind());
        assertEquals("my-proj.ds.orderz", missing.subject());
        assertEquals("US", missing.qualifier());
        assertFalse(missing.hasPosition());
        assertParsed("Table name \"orders\" missing dataset while no default dataset is set in the request.",
                MISSING_DATASET, "orders", null);
    }

    @Test
    public void queryStructureErrorsAreTakenApart() {
        assertParsed("Column name customer_id is ambiguous at [3:8]", AMBIGUOUS_COLUMN, "customer_id", null);
        assertParsed("SELECT list expression references column amount which is neither grouped nor aggregated at [2:3]",
                NOT_GROUPED_OR_AGGREGATED, "amount", "SELECT list");
        assertParsed("SELECT list expression references o.amount which is neither grouped nor aggregated at [2:3]",
                NOT_GROUPED_OR_AGGREGATED, "o.amount", "SELECT list");
        assertParsed("Aggregate function SUM not allowed in WHERE clause at [4:7]", AGGREGATE_NOT_ALLOWED, "SUM", "WHERE");
        assertParsed("Analytic function not allowed in WHERE clause at [4:7]", ANALYTIC_NOT_ALLOWED, null, "WHERE");
        assertParsed("Duplicate column names in the result are not supported. Found duplicate(s): order_id",
                DUPLICATE_COLUMN, "order_id", null);
        assertParsed("Cannot access field city on a value with type ARRAY<STRUCT<city STRING>> at [1:10]",
                FIELD_ACCESS_ON_ARRAY, "city", "ARRAY<STRUCT<city STRING>>");
    }

    @Test
    public void aSignatureMismatchKeepsTheSupportedSignatures() {
        BigQueryError error = BigQueryErrorParser.parse("No matching signature for function DATE_DIFF for argument types: "
                + "DATE, TIMESTAMP, DATE_TIME_PART. Supported signature: DATE_DIFF(DATE, DATE, DATE_TIME_PART) at [1:8]");

        assertEquals(NO_MATCHING_SIGNATURE, error.kind());
        assertEquals("DATE_DIFF", error.subject());
        assertTrue(error.qualifier().contains("Supported signature: DATE_DIFF(DATE, DATE, DATE_TIME_PART)"));
    }

    @Test
    public void aMultiLineMessageIsReadAsOneLine() {
        BigQueryError error = BigQueryErrorParser.parse("No matching signature for function DATE_DIFF\n"
                + "  Argument types: DATE, TIMESTAMP\n  Signature: DATE_DIFF(DATE, DATE, DATE_TIME_PART) at [1:8]");

        assertEquals(NO_MATCHING_SIGNATURE, error.kind());
        assertEquals("DATE_DIFF", error.subject());
        assertEquals(1, error.line());
        assertEquals(8, error.column());
    }

    @Test
    public void syntaxErrorsAreTakenApart() {
        assertParsed("Syntax error: Unexpected keyword FROM at [3:1]", UNEXPECTED_TOKEN, "FROM", null);
        assertParsed("Syntax error: Unexpected identifier \"order_ts\" at [4:3]", UNEXPECTED_TOKEN, "order_ts", null);
        assertParsed("Syntax error: Unexpected \",\" at [2:9]", UNEXPECTED_TOKEN, ",", null);
        assertParsed("Syntax error: Expected end of input but got keyword SELECT at [5:1]",
                EXPECTED_END_OF_INPUT, "SELECT", null);
        assertParsed("Syntax error: Expected \")\" but got keyword FROM at [2:20]", UNCLOSED_PARENTHESIS, "FROM", null);
        assertParsed("Syntax error: Expected \")\" or \",\" but got end of script at [3:1]", UNCLOSED_PARENTHESIS, null, null);
        assertParsed("Syntax error: Unclosed string literal at [1:8]", UNCLOSED_STRING, null, null);
        assertParsed("Syntax error: Unexpected end of script at [6:1]", UNEXPECTED_END, null, null);
        assertParsed("Syntax error: Illegal input character \"\u201C\" at [1:8]", ILLEGAL_CHARACTER, "\u201C", null);
        assertParsed("Syntax error: Expected keyword BY but got identifier \"x\" at [3:10]",
                UNEXPECTED_TOKEN, "x", "keyword BY");
        assertParsed("Syntax error: Something new at [1:1]", SYNTAX_ERROR, null, null);
    }

    @Test
    public void anUnknownMessageIsKeptWhole() {
        BigQueryError error = BigQueryErrorParser.parse("Resources exceeded during query execution");

        assertEquals(OTHER, error.kind());
        assertEquals("Resources exceeded during query execution", error.message());
        assertNull(error.subject());
        assertFalse(error.hasPosition());
    }
}
