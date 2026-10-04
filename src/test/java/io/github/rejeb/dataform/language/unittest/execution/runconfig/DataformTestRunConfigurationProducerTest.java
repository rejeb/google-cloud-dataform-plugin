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

import com.google.gson.Gson;
import com.intellij.execution.PsiLocation;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.compilation.DataformCompilationService;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.unittest.execution.engine.DataformTestScope;
import io.github.rejeb.dataform.language.gcp.execution.workflow.runconfig.DataformWorkflowRunConfiguration;
import io.github.rejeb.dataform.language.gcp.execution.workflow.runconfig.SqlxEditorGutterProvider;
import io.github.rejeb.dataform.language.psi.SqlxConfigBlock;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Objects;

public class DataformTestRunConfigurationProducerTest extends BasePlatformTestCase {

    private static final String TEST_FILE = "config {\n  type: \"test\",\n  dataset: \"orders\",\n  tags: [\"unit\"]\n}\n\n"
            + "input \"raw\" {\n  SELECT 1 AS id\n}\n\nSELECT 1 AS id\n";

    private List<ConfigurationFromContext> configurationsFor(PsiElement element) {
        List<ConfigurationFromContext> configurations = ConfigurationContext
                .createEmptyContextForLocation(PsiLocation.fromPsiElement(element))
                .createConfigurationsFromContext();
        return configurations == null ? List.of() : configurations;
    }

    public void testTestFileGetsOnlyTheTestConfiguration() {
        PsiFile file = myFixture.addFileToProject("definitions/orders_test.sqlx", TEST_FILE);

        List<ConfigurationFromContext> configurations = configurationsFor(file);

        assertEquals(1, configurations.size());
        DataformTestRunConfiguration configuration =
                assertInstanceOf(configurations.getFirst().getConfiguration(), DataformTestRunConfiguration.class);
        assertEquals(DataformTestScope.FILE, configuration.getScope());
        assertEquals("definitions/orders_test.sqlx", configuration.getTargetPath());
        assertEquals("orders_test", configuration.getName());
    }

    public void testTableFileGetsNoTestConfiguration() {
        PsiFile file = myFixture.addFileToProject("definitions/orders.sqlx", "config { type: \"table\" }\nSELECT 1\n");

        assertFalse(configurationsFor(file).stream()
                .anyMatch(c -> c.getConfiguration() instanceof DataformTestRunConfiguration));
    }

    public void testADirectoryWithCompiledTestsRunsThem() throws Exception {
        PsiFile file = myFixture.addFileToProject("definitions/tests/orders_test.sqlx", TEST_FILE);
        installGraph("{\"tests\": [{\"name\": \"orders_test\", \"fileName\": \"definitions/tests/orders_test.sqlx\"}],"
                + " \"graphErrors\": {\"compilationErrors\": []}}");

        List<ConfigurationFromContext> configurations = configurationsFor(file.getContainingDirectory());

        DataformTestRunConfiguration configuration = (DataformTestRunConfiguration) configurations.stream()
                .map(ConfigurationFromContext::getConfiguration)
                .filter(c -> c instanceof DataformTestRunConfiguration)
                .findFirst().orElseThrow();
        assertEquals(DataformTestScope.DIRECTORY, configuration.getScope());
        assertEquals("definitions/tests", configuration.getTargetPath());
    }

    public void testTheProjectRootRunsAllTestsWithAValidConfiguration() throws Exception {
        PsiFile file = myFixture.addFileToProject("definitions/tests/orders_test.sqlx", TEST_FILE);
        installGraph("{\"tests\": [{\"name\": \"orders_test\", \"fileName\": \"definitions/tests/orders_test.sqlx\"}],"
                + " \"graphErrors\": {\"compilationErrors\": []}}");
        PsiDirectory root = PsiManager.getInstance(getProject()).findDirectory(
                Objects.requireNonNull(ProjectUtil.guessProjectDir(getProject())));

        DataformTestRunConfiguration configuration = (DataformTestRunConfiguration) configurationsFor(root).stream()
                .map(ConfigurationFromContext::getConfiguration)
                .filter(c -> c instanceof DataformTestRunConfiguration)
                .findFirst().orElseThrow();

        assertNotNull(file);
        assertEquals(DataformTestScope.ALL, configuration.getScope());
        assertEquals("All Dataform tests", configuration.getName());
        configuration.checkConfiguration();
    }

    public void testWorkflowGutterSkipsTestFiles() {
        PsiFile file = myFixture.addFileToProject("definitions/orders_test.sqlx", TEST_FILE);
        PsiElement firstLeaf = PsiTreeUtil.getDeepestFirst(PsiTreeUtil.findChildOfType(file, SqlxConfigBlock.class));

        assertNull(new SqlxEditorGutterProvider().getLineMarkerInfo(firstLeaf));
        assertNotNull(new DataformTestRunLineMarkerContributor().getInfo(firstLeaf));
        assertFalse(configurationsFor(firstLeaf).stream()
                .anyMatch(c -> c.getConfiguration() instanceof DataformWorkflowRunConfiguration));
    }

    private void installGraph(String json) throws ReflectiveOperationException {
        DataformCompilationService service = getProject().getService(DataformCompilationService.class);
        Field field = service.getClass().getDeclaredField("compiledGraph");
        field.setAccessible(true);
        Object previous = field.get(service);
        Disposer.register(getTestRootDisposable(), () -> {
            try {
                field.set(service, previous);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        });
        field.set(service, new Gson().fromJson(json, CompiledGraph.class));
    }
}
