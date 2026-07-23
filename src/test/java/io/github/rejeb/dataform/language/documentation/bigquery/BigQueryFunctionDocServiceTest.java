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
package io.github.rejeb.dataform.language.documentation.bigquery;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.Collection;

public class BigQueryFunctionDocServiceTest extends BasePlatformTestCase {

    public void testFindIsCaseInsensitive() {
        BigQueryFunctionDocService service = BigQueryFunctionDocService.getInstance();
        assertTrue(service.find("date_trunc").isPresent());
        assertTrue(service.find("DATE_TRUNC").isPresent());
        assertEquals("DATE_TRUNC", service.find("Date_Trunc").orElseThrow().name());
    }

    public void testUnknownFunctionIsEmpty() {
        assertTrue(BigQueryFunctionDocService.getInstance().find("not_a_bq_function").isEmpty());
    }

    public void testIsKnownFunction() {
        BigQueryFunctionDocService service = BigQueryFunctionDocService.getInstance();
        assertTrue(service.isKnownFunction("sum"));
        assertFalse(service.isKnownFunction("my_udf"));
    }

    public void testEveryEntryIsWellFormed() {
        Collection<BigQueryFunctionDoc> all = BigQueryFunctionDocService.getInstance().getAll();
        assertTrue("expected a curated set of functions but got " + all.size(), all.size() >= 70);
        for (BigQueryFunctionDoc doc : all) {
            assertNotNull(doc.name());
            assertFalse(doc.name().isBlank());
            assertNotNull("no signatures for " + doc.name(), doc.signatures());
            assertFalse("no signatures for " + doc.name(), doc.signatures().isEmpty());
            assertNotNull("no description for " + doc.name(), doc.description());
            assertFalse("no description for " + doc.name(), doc.description().isBlank());
            assertNotNull("no docUrl for " + doc.name(), doc.docUrl());
            assertTrue("bad docUrl for " + doc.name(), doc.docUrl().startsWith("https://"));
        }
    }
}
