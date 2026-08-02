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
package io.github.rejeb.dataform.language.evaluation;

import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.compilation.DataformCompilationService;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.compilation.model.ProjectConfig;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

public class DataformEvaluationContextBuilderTest extends BasePlatformTestCase {

    public void testVarsComeFromWorkflowSettings() {
        myFixture.addFileToProject("workflow_settings.yaml", """
                defaultProject: my-project
                defaultDataset: my_dataset
                vars:
                  bronze: bronze_dataset
                """);
        PsiFile file = myFixture.addFileToProject("definitions/mart.sqlx", "SELECT 1 AS one\n");

        DataformEvaluationContext context =
                DataformEvaluationContextBuilder.build(getProject(), file, List.of());

        assertEquals("my-project", context.projectConfig().get("defaultDatabase"));
        assertEquals("my_dataset", context.projectConfig().get("defaultSchema"));
        assertEquals("bronze_dataset", vars(context).get("bronze"));
    }

    public void testWorkflowSettingsVarsSurviveAPartialCompiledProjectConfig() throws Exception {
        myFixture.addFileToProject("workflow_settings.yaml", """
                defaultProject: my-project
                vars:
                  bronze: bronze_dataset
                """);
        PsiFile file = myFixture.addFileToProject("definitions/mart.sqlx", "SELECT 1 AS one\n");

        CompiledGraph graph = new CompiledGraph();
        ProjectConfig projectConfig = new ProjectConfig();
        set(projectConfig, "defaultDatabase", "compiled-project");
        set(graph, "projectConfig", projectConfig);
        set(getProject().getService(DataformCompilationService.class), "compiledGraph", graph);

        DataformEvaluationContext context =
                DataformEvaluationContextBuilder.build(getProject(), file, List.of());

        assertEquals("compiled-project", context.projectConfig().get("defaultDatabase"));
        assertEquals("the compiled config must not drop settings-only keys",
                "bronze_dataset", vars(context).get("bronze"));
    }

    public void testVarsIsAlwaysAnObject() {
        PsiFile file = myFixture.addFileToProject("definitions/mart.sqlx", "SELECT 1 AS one\n");

        DataformEvaluationContext context =
                DataformEvaluationContextBuilder.build(getProject(), file, List.of());

        assertNotNull("vars must exist so vars.x yields undefined instead of a type error",
                context.projectConfig().get("vars"));
    }

    public void testJsBlockBecomesTheFileScript() {
        PsiFile file = myFixture.addFileToProject("definitions/mart.sqlx", """
                config { type: "table" }

                js {
                  const SUFFIX = "_v1";
                  function tbl(name) { return name + SUFFIX; }
                }

                SELECT * FROM ${tbl("orders")}
                """);

        DataformEvaluationContext context =
                DataformEvaluationContextBuilder.build(getProject(), file, List.of());

        assertNotNull(context.fileScript());
        assertTrue(context.fileScript(), context.fileScript().contains("const SUFFIX = \"_v1\";"));
        assertTrue(context.fileScript(), context.fileScript().contains("function tbl(name)"));
    }

    public void testFileScriptIsNullWithoutJsBlock() {
        PsiFile file = myFixture.addFileToProject("definitions/mart.sqlx",
                "config { type: \"table\" }\n\nSELECT 1 AS one\n");

        assertNull(DataformEvaluationContextBuilder.build(getProject(), file, List.of()).fileScript());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> vars(DataformEvaluationContext context) {
        return (Map<String, Object>) context.projectConfig().get("vars");
    }

    private static void set(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
