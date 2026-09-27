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

import io.github.rejeb.dataform.language.gcp.execution.unittest.engine.UnitTestCase;
import io.github.rejeb.dataform.language.gcp.execution.unittest.engine.UnitTestOutcome;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceMessageUnitTestListenerTest {

    private static final UnitTestCase TEST =
            new UnitTestCase("orders_test", "definitions/orders_test.sqlx", "A", "B", false, null);

    @Test
    void aFailedComparisonCarriesExpectedAndActual() {
        StringBuilder out = new StringBuilder();
        ServiceMessageUnitTestListener listener = new ServiceMessageUnitTestListener(out::append);

        listener.runStarted(1);
        listener.testStarted(TEST);
        listener.testFinished(TEST, UnitTestOutcome.failed(List.of("Expected 2 rows, but saw 1 rows."), "id\n1\n2", "id\n1"), 12);

        String text = out.toString();
        assertTrue(text.contains("##teamcity[testCount count='1']"), text);
        assertTrue(text.contains("##teamcity[testStarted name='orders_test' locationHint='dataform-test://definitions/orders_test.sqlx']"), text);
        assertTrue(text.contains("testFailed name='orders_test' message='Expected 2 rows, but saw 1 rows.' expected='id|n1|n2' actual='id|n1' type='comparisonFailure'"), text);
        assertTrue(text.contains("##teamcity[testFinished name='orders_test' duration='12']"), text);
        assertEquals(1, listener.failureCount());
    }

    @Test
    void passedAndIgnoredTestsAreNotFailures() {
        StringBuilder out = new StringBuilder();
        ServiceMessageUnitTestListener listener = new ServiceMessageUnitTestListener(out::append);

        listener.testStarted(TEST);
        listener.testFinished(TEST, UnitTestOutcome.success(), 3);
        listener.testIgnored(TEST, "Disabled");

        assertTrue(out.toString().contains("##teamcity[testIgnored name='orders_test' message='Disabled']"), out.toString());
        assertEquals(0, listener.failureCount());
    }
}
