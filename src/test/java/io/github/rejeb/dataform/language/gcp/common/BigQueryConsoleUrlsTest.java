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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BigQueryConsoleUrlsTest {

    @Test
    void aTableOpensInTheExplorerOfItsProject() {
        assertEquals("https://console.cloud.google.com/bigquery?project=p&ws=!1m5!1m4!4m3!1sp!2sd!3st",
                BigQueryConsoleUrls.tableUrl("p.d.t"));
    }

    @Test
    void aProjectWithDashesIsKeptAsItIs() {
        assertEquals("https://console.cloud.google.com/bigquery?project=sales-prod&ws=!1m5!1m4!4m3!1ssales-prod!2sraw!3sorders",
                BigQueryConsoleUrls.tableUrl("sales-prod.raw.orders"));
    }

    @Test
    void aNameThatIsNotATableOpensTheConsole() {
        assertEquals("https://console.cloud.google.com/bigquery", BigQueryConsoleUrls.tableUrl("orders"));
    }

    @Test
    void theLineageOfATableOpensItsTablePage() {
        assertEquals(BigQueryConsoleUrls.tableUrl("p.d.t"), BigQueryConsoleUrls.lineageUrl("p.d.t"));
    }
}
