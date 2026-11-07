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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BuildSummaryTextTest {

    @Test
    void testTheSummaryCountsActionsErrorsAndFiles() {
        assertEquals("Compiled 4 action(s), 2 compilation error(s) in 1 file(s).\n"
                        + "1 of 3 action queries failed in BigQuery.\n",
                BuildSummaryText.of(4, 2, 1, "1 of 3 action queries failed in BigQuery"));
    }

    @Test
    void testWithoutABigQueryCheckOnlyTheCompilationIsSummarized() {
        assertEquals("Compiled 0 action(s), 1 compilation error(s) in 1 file(s).\n",
                BuildSummaryText.of(0, 1, 1, null));
    }

    @Test
    void testTheBigQuerySentenceStartsWithACapital() {
        assertEquals("Compiled 3 action(s), 0 compilation error(s) in 0 file(s).\n"
                        + "Action queries not checked in BigQuery.\n",
                BuildSummaryText.of(3, 0, 0, "action queries not checked in BigQuery"));
    }

    @Test
    void testTheHeaderShowsTheCommandAndTheDirectory() {
        CliRun run = new CliRun("dataform compile --json", "/work/project", null, 0, false, "", 10);

        assertEquals("$ dataform compile --json\n  in /work/project\n\n", BuildSummaryText.header(run));
    }

    @Test
    void testWithoutARunThereIsNoHeader() {
        assertEquals("", BuildSummaryText.header(null));
    }

    @Test
    void testTheProcessLineSaysHowTheRunEnded() {
        assertEquals("Process finished with exit code 1\n",
                BuildSummaryText.processEnd(new CliRun("c", "d", null, 1, true, "", 10)));
        assertEquals("Process not started\n",
                BuildSummaryText.processEnd(new CliRun("c", "d", null, null, true, "", 10)));
    }
}
