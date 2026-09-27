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

import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class UnitTestRunner {

    private static final String DISABLED = "Disabled";

    private final UnitTestQueryExecutor executor;
    private final UnitTestListener listener;

    public UnitTestRunner(@NotNull UnitTestQueryExecutor executor, @NotNull UnitTestListener listener) {
        this.executor = executor;
        this.listener = listener;
    }

    /**
     * Runs the tests one after the other, reporting each to the listener. Stops before the next test
     * when the indicator is cancelled.
     */
    public void run(@NotNull List<UnitTestCase> tests, @NotNull ProgressIndicator indicator) {
        listener.runStarted(tests.size());
        try {
            for (UnitTestCase test : tests) {
                indicator.checkCanceled();
                if (test.disabled()) {
                    listener.testIgnored(test, DISABLED);
                    continue;
                }
                listener.testStarted(test);
                long start = System.nanoTime();
                UnitTestOutcome outcome = runOne(test, indicator);
                listener.testFinished(test, outcome, (System.nanoTime() - start) / 1_000_000);
            }
        } finally {
            listener.runFinished();
        }
    }

    @NotNull
    private UnitTestOutcome runOne(@NotNull UnitTestCase test, @NotNull ProgressIndicator indicator) {
        if (test.compilationError() != null) {
            return UnitTestOutcome.failed(List.of(test.compilationError()), null, null);
        }
        try {
            UnitTestRows actual = executor.execute(test.testQuery(), indicator);
            UnitTestRows expected = executor.execute(test.expectedOutputQuery(), indicator);
            return UnitTestComparator.compare(actual, expected);
        } catch (UnitTestQueryException e) {
            return UnitTestOutcome.error(String.valueOf(e.getMessage()));
        }
    }
}
