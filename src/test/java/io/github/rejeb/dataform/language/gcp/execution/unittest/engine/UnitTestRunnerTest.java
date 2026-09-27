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
package io.github.rejeb.dataform.language.gcp.execution.unittest.engine;

import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class UnitTestRunnerTest extends BasePlatformTestCase {

    private static final class RecordingListener implements UnitTestListener {
        final List<String> events = new ArrayList<>();

        @Override
        public void runStarted(int count) {
            events.add("run " + count);
        }

        @Override
        public void testStarted(UnitTestCase test) {
            events.add("start " + test.name());
        }

        @Override
        public void testFinished(UnitTestCase test, UnitTestOutcome outcome, long durationMs) {
            events.add((outcome.passed() ? "pass " : "fail ") + test.name() + " " + outcome.messages());
        }

        @Override
        public void testIgnored(UnitTestCase test, String reason) {
            events.add("ignore " + test.name());
        }

        @Override
        public void runFinished() {
            events.add("done");
        }
    }

    private static UnitTestRows one(String value) {
        return new UnitTestRows(List.of("v"), List.of(List.of(new BigDecimal(value))));
    }

    private static UnitTestCase test(String name, String testQuery, String expected) {
        return new UnitTestCase(name, "definitions/" + name + ".sqlx", testQuery, expected, false, null);
    }

    private static UnitTestQueryExecutor executor(Map<String, UnitTestRows> results) {
        return (sql, indicator) -> {
            UnitTestRows rows = results.get(sql);
            if (rows == null) throw new UnitTestQueryException("Unrecognized name: x at [1:8]");
            return rows;
        };
    }

    public void testReportsPassFailErrorIgnoreAndCompileError() {
        RecordingListener listener = new RecordingListener();
        UnitTestQueryExecutor executor = executor(Map.of("A", one("1"), "B", one("2")));

        new UnitTestRunner(executor, listener).run(List.of(
                test("ok", "A", "A"),
                test("ko", "A", "B"),
                test("boom", "BAD", "A"),
                new UnitTestCase("off", "definitions/off.sqlx", "A", "A", true, null),
                new UnitTestCase("broken", "definitions/broken.sqlx", "", "", false, "Input for dataset \"raw\" has not been provided.")),
                new EmptyProgressIndicator());

        assertEquals(List.of(
                "run 5",
                "start ok", "pass ok []",
                "start ko", "fail ko [For row 0 and column \"v\": expected \"2\", but saw \"1\".]",
                "start boom", "fail boom [Error thrown: Unrecognized name: x at [1:8].]",
                "ignore off",
                "start broken", "fail broken [Input for dataset \"raw\" has not been provided.]",
                "done"), listener.events);
    }

    public void testACancelledRunStopsBeforeTheNextTest() {
        RecordingListener listener = new RecordingListener();
        ProgressIndicator indicator = new EmptyProgressIndicator();
        UnitTestQueryExecutor executor = (sql, progress) -> {
            progress.cancel();
            return one("1");
        };

        assertThrows(ProcessCanceledException.class, () -> new UnitTestRunner(executor, listener)
                .run(List.of(test("first", "A", "A"), test("second", "A", "A")), indicator));

        assertEquals(List.of("run 2", "start first", "pass first []", "done"), listener.events);
    }

    public void testAnUnexpectedFailureErrsThatTestAndTheRunGoesOn() {
        RecordingListener listener = new RecordingListener();
        UnitTestQueryExecutor executor = (sql, indicator) -> {
            if (sql.equals("NETWORK")) throw new IllegalStateException("Connection reset");
            return one("1");
        };

        new UnitTestRunner(executor, listener).run(List.of(
                test("flaky", "NETWORK", "A"),
                test("next", "A", "A")), new EmptyProgressIndicator());

        assertEquals(List.of(
                "run 2",
                "start flaky", "fail flaky [Error thrown: Connection reset.]",
                "start next", "pass next []",
                "done"), listener.events);
    }
}
