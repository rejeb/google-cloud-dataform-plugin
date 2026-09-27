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
package io.github.rejeb.dataform.language.gcp.execution.unittest.runconfig;

import com.intellij.execution.testframework.sm.ServiceMessageBuilder;
import io.github.rejeb.dataform.language.gcp.execution.unittest.engine.UnitTestCase;
import io.github.rejeb.dataform.language.gcp.execution.unittest.engine.UnitTestListener;
import io.github.rejeb.dataform.language.gcp.execution.unittest.engine.UnitTestOutcome;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * Reports the runner's events as TeamCity service messages, which the SM test console turns into a
 * test tree with a diff viewer for failed comparisons.
 */
public final class ServiceMessageUnitTestListener implements UnitTestListener {

    private final Consumer<String> output;
    private int failures;

    public ServiceMessageUnitTestListener(@NotNull Consumer<String> output) {
        this.output = output;
    }

    @Override
    public void runStarted(int testCount) {
        print(new ServiceMessageBuilder("testCount").addAttribute("count", String.valueOf(testCount)));
    }

    @Override
    public void testStarted(@NotNull UnitTestCase test) {
        print(ServiceMessageBuilder.testStarted(test.name())
                .addAttribute("locationHint", DataformTestLocator.locationHint(test.fileName())));
    }

    @Override
    public void testFinished(@NotNull UnitTestCase test, @NotNull UnitTestOutcome outcome, long durationMs) {
        if (!outcome.passed()) {
            failures++;
            ServiceMessageBuilder failed = ServiceMessageBuilder.testFailed(test.name())
                    .addAttribute("message", String.join("\n", outcome.messages()));
            if (outcome.expected() != null && outcome.actual() != null) {
                failed.addAttribute("expected", outcome.expected())
                        .addAttribute("actual", outcome.actual())
                        .addAttribute("type", "comparisonFailure");
            }
            print(failed);
        }
        print(ServiceMessageBuilder.testFinished(test.name()).addAttribute("duration", String.valueOf(durationMs)));
    }

    @Override
    public void testIgnored(@NotNull UnitTestCase test, @NotNull String reason) {
        testStarted(test);
        print(ServiceMessageBuilder.testIgnored(test.name()).addAttribute("message", reason));
        print(ServiceMessageBuilder.testFinished(test.name()));
    }

    @Override
    public void runFinished() {
    }

    /**
     * Returns the number of tests that failed so far.
     */
    public int failureCount() {
        return failures;
    }

    private void print(@NotNull ServiceMessageBuilder message) {
        output.accept(message + "\n");
    }
}
