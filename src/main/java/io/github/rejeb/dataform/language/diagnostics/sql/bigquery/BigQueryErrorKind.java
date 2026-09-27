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

/**
 * What a BigQuery error is about, as far as a hint for fixing it is concerned.
 */
public enum BigQueryErrorKind {
    UNRECOGNIZED_NAME,
    NAME_NOT_FOUND_INSIDE,
    FIELD_NOT_FOUND,
    FUNCTION_NOT_FOUND,
    TABLE_NOT_FOUND,
    MISSING_DATASET,
    AMBIGUOUS_COLUMN,
    NOT_GROUPED_OR_AGGREGATED,
    AGGREGATE_NOT_ALLOWED,
    ANALYTIC_NOT_ALLOWED,
    DUPLICATE_COLUMN,
    FIELD_ACCESS_ON_ARRAY,
    NO_MATCHING_SIGNATURE,
    UNEXPECTED_TOKEN,
    EXPECTED_END_OF_INPUT,
    UNCLOSED_PARENTHESIS,
    UNCLOSED_STRING,
    UNEXPECTED_END,
    ILLEGAL_CHARACTER,
    SYNTAX_ERROR,
    ACCESS_DENIED,
    OTHER
}
