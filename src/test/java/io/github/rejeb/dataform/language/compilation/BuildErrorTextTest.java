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
package io.github.rejeb.dataform.language.compilation;

import com.google.gson.Gson;
import io.github.rejeb.dataform.language.compilation.model.CompilationError;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BuildErrorTextTest {

    private static final Gson GSON = new Gson();

    private CompilationError error(String json) {
        return GSON.fromJson(json, CompilationError.class);
    }

    @Test
    void titleCarriesTheActionAndTheMessage() {
        CompilationError error = error("{\"actionName\":\"bronze.customers\",\"message\":\"boom\"}");

        assertEquals("[bronze.customers] boom", BuildErrorText.title(error));
    }

    @Test
    void titleKeepsTheFirstMeaningfulLineOfAMultiLineMessage() {
        CompilationError error = error("{\"stack\":\"\\n\\nError: unexpected token\\n"
                + "    at compile (index.js:1:1)\\n    at run (index.js:2:2)\"}");

        assertEquals("Error: unexpected token", BuildErrorText.title(error));
    }

    @Test
    void titleIsTruncatedSoTheTreeNodeStaysReadable() {
        CompilationError error = error("{\"message\":\"" + "z".repeat(400) + "\"}");

        String title = BuildErrorText.title(error);
        assertTrue(title.length() <= BuildErrorText.MAX_TITLE_LENGTH,
                "title too long: " + title.length());
        assertTrue(title.endsWith("…"), "a truncated title must be marked as such: " + title);
    }

    @Test
    void titleFallsBackWhenNothingIsReported() {
        assertEquals("(no message)", BuildErrorText.title(error("{}")));
    }

    @Test
    void detailsCarryTheWholeMessage() {
        CompilationError error = error("{\"message\":\"" + "z".repeat(400) + "\"}");

        String details = BuildErrorText.details(error);
        assertEquals(400, details.replace("\n", "").length(),
                "the details panel must show the message in full: " + details);
    }

    @Test
    void detailsWrapLongLines() {
        CompilationError error = error("{\"message\":\"" + "z".repeat(400) + "\"}");

        for (String line : BuildErrorText.details(error).split("\n")) {
            assertTrue(line.length() <= BuildErrorText.MAX_LINE_LENGTH,
                    "line too long: " + line.length() + " -> " + line);
        }
    }

    @Test
    void detailsKeepTheStackLineStructure() {
        CompilationError error = error("{\"stack\":\"Error: boom\\n    at compile (index.js:1:1)\"}");

        String details = BuildErrorText.details(error);
        assertTrue(details.contains("Error: boom\n"), "stack lines must be kept: " + details);
        assertTrue(details.contains("at compile (index.js:1:1)"), details);
    }

    @Test
    void detailsNameTheFailingFileAndAction() {
        CompilationError error = error("{\"actionName\":\"bronze.customers\","
                + "\"fileName\":\"definitions/bronze/customers.sqlx\",\"message\":\"boom\"}");

        String details = BuildErrorText.details(error);
        assertTrue(details.contains("bronze.customers"), details);
        assertTrue(details.contains("definitions/bronze/customers.sqlx"), details);
        assertTrue(details.contains("boom"), details);
    }

    @Test
    void detailsShowTheMessageAndTheStackWhenBothArePresent() {
        CompilationError error = error("{\"message\":\"boom\",\"stack\":\"Error: boom\\n    at compile\"}");

        String details = BuildErrorText.details(error);
        assertTrue(details.contains("boom"), details);
        assertTrue(details.contains("at compile"), details);
    }

    @Test
    void detailsFallBackWhenNothingIsReported() {
        assertEquals("(no message)", BuildErrorText.details(error("{}")));
    }

    @Test
    void freeFormDetailsAreWrappedToo() {
        String details = BuildErrorText.details("y".repeat(300));

        for (String line : details.split("\n")) {
            assertTrue(line.length() <= BuildErrorText.MAX_LINE_LENGTH, line);
        }
        assertEquals(300, details.replace("\n", "").length());
    }
}
