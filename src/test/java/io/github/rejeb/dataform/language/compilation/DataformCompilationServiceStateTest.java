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

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.compilation.DataformCompilationService.State;
import io.github.rejeb.dataform.language.compilation.model.Target;

public class DataformCompilationServiceStateTest extends BasePlatformTestCase {

    private static final long COMPILED_AT = 1_793_000_000_000L;
    private static final String CLI_OUTPUT = """
            {"assertions":[{"target":{"database":"p","schema":"ds_assertions","name":"ds_users_assertions_uniqueKey_0"},\
            "parentAction":{"database":"p","schema":"ds","name":"users"},"query":"SELECT 1",\
            "fileName":"definitions/users.sqlx"}],"dataformCoreVersion":"3.0.10"}""";

    public void testAGraphSavedByAnOlderVersionIsDroppedSoTheProjectCompilesAgain() {
        DataformCompilationServiceImpl service = new DataformCompilationServiceImpl(getProject());
        State saved = new State();
        saved.compiledGraphJson = CLI_OUTPUT;
        saved.lastCompileTimestamp = COMPILED_AT;

        service.loadState(saved);

        assertNull(service.getCompiledGraph());
        assertEquals(-1, service.lastCompiledAt());
    }

    public void testTheSavedGraphKeepsEveryFieldTheCliPrinted() {
        DataformCompilationServiceImpl service = new DataformCompilationServiceImpl(getProject());

        service.rememberCompilation(CLI_OUTPUT, COMPILED_AT);

        assertEquals(CLI_OUTPUT, service.getState().compiledGraphJson);
        assertEquals(State.RAW_CLI_OUTPUT, service.getState().compiledGraphFormat);
    }

    public void testTheSavedGraphDropsTheIndentationOfTheCliOutput() {
        DataformCompilationServiceImpl service = new DataformCompilationServiceImpl(getProject());

        service.rememberCompilation("{\n    \"dataformCoreVersion\": \"3.0.10\",\n    \"tables\": []\n}\n", COMPILED_AT);

        assertEquals("{\"dataformCoreVersion\":\"3.0.10\",\"tables\":[]}", service.getState().compiledGraphJson);
    }

    public void testARestartRestoresTheParentActionOfTheAssertions() {
        DataformCompilationServiceImpl before = new DataformCompilationServiceImpl(getProject());
        before.rememberCompilation(CLI_OUTPUT, COMPILED_AT);
        DataformCompilationServiceImpl after = new DataformCompilationServiceImpl(getProject());

        after.loadState(before.getState());

        Target parent = after.getCompiledGraph().getAssertions().getFirst().getParentAction();
        assertNotNull(parent);
        assertEquals("users", parent.getName());
        assertEquals(COMPILED_AT, after.lastCompiledAt());
    }
}
