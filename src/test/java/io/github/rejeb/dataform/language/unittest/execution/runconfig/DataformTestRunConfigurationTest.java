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
package io.github.rejeb.dataform.language.unittest.execution.runconfig;

import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.configurations.RuntimeConfigurationError;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.unittest.execution.engine.DataformTestScope;

public class DataformTestRunConfigurationTest extends BasePlatformTestCase {

    private DataformTestRunConfiguration configuration() {
        return (DataformTestRunConfiguration) ConfigurationTypeUtil
                .findConfigurationType(DataformTestConfigurationType.class)
                .getConfigurationFactories()[0]
                .createTemplateConfiguration(getProject());
    }

    public void testDefaultsToAllTests() throws Exception {
        DataformTestRunConfiguration configuration = configuration();

        assertEquals(DataformTestScope.ALL, configuration.getScope());
        configuration.checkConfiguration();
    }

    public void testAFileScopeNeedsAPath() {
        DataformTestRunConfiguration configuration = configuration();
        configuration.setScope(DataformTestScope.FILE);

        assertThrows(RuntimeConfigurationError.class, configuration::checkConfiguration);
        configuration.setTargetPath("definitions\\orders_test.sqlx");
        assertEquals("definitions/orders_test.sqlx", configuration.getTargetPath());
    }

    public void testTheRunnerRunsOnlyTestConfigurationsWithTheRunExecutor() {
        DataformTestProgramRunner runner = new DataformTestProgramRunner();

        assertTrue(runner.canRun(DefaultRunExecutor.EXECUTOR_ID, configuration()));
        assertFalse(runner.canRun(DefaultDebugExecutor.EXECUTOR_ID, configuration()));
    }
}
