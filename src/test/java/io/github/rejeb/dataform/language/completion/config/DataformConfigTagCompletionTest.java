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
package io.github.rejeb.dataform.language.completion.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.compilation.DataformCompilationService;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;

import java.util.List;

public class DataformConfigTagCompletionTest extends BasePlatformTestCase {

    private static final String GRAPH_JSON = """
            {
              "tables": [
                {"target": {"name": "sales"}, "tags": ["daily", "finance"]},
                {"target": {"name": "users"}, "tags": ["daily", "core"]}
              ],
              "assertions": [{"target": {"name": "check"}, "tags": ["quality"]}],
              "operations": [],
              "declarations": []
            }""";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        CompiledGraph graph = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .readValue(GRAPH_JSON, CompiledGraph.class);
        ServiceContainerUtil.replaceService(getProject(), DataformCompilationService.class,
                new StubCompilationService(graph), getTestRootDisposable());
    }

    private List<String> completeIn(String configBlock) {
        PsiFile file = myFixture.addFileToProject("definitions/mart.sqlx",
                configBlock + "\n\nSELECT 1\n");
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        myFixture.completeBasic();
        List<String> lookups = myFixture.getLookupElementStrings();
        return lookups == null ? List.of() : lookups;
    }

    public void testProjectTagsAreProposedInsideTagsArray() {
        List<String> lookups = completeIn(
                "config {\n  type: \"table\",\n  tags: [\"<caret>\"]\n}");

        assertTrue("got " + lookups, lookups.contains("daily"));
        assertTrue("got " + lookups, lookups.contains("finance"));
        assertTrue("got " + lookups, lookups.contains("quality"));
    }

    public void testAlreadyDeclaredTagsAreFilteredOut() {
        List<String> lookups = completeIn(
                "config {\n  type: \"table\",\n  tags: [\"daily\", \"<caret>\"]\n}");

        assertFalse("got " + lookups, lookups.contains("daily"));
        assertTrue("got " + lookups, lookups.contains("core"));
    }

    public void testTagsAreNotProposedOutsideTheTagsArray() {
        List<String> lookups = completeIn(
                "config {\n  type: \"table\",\n  description: \"<caret>\"\n}");

        assertFalse("got " + lookups, lookups.contains("daily"));
    }

    private record StubCompilationService(CompiledGraph graph) implements DataformCompilationService {

        @Override
        public CompiledGraph compile(boolean forceRefresh) {
            return graph;
        }

        @Override
        public CompiledGraph getCompiledGraph() {
            return graph;
        }

        @Override
        public State getState() {
            return new State();
        }

        @Override
        public void loadState(@org.jetbrains.annotations.NotNull State state) {
        }

        @Override
        public void dispose() {
        }
    }
}
