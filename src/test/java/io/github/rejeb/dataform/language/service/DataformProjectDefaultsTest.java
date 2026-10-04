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
package io.github.rejeb.dataform.language.service;

import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.compilation.model.ProjectConfig;

import java.lang.reflect.Field;

/**
 * The defaults of a project are what the last compilation used, and what {@code workflow_settings.yaml}
 * says where the compilation recorded nothing, whichever feature asks.
 */
public class DataformProjectDefaultsTest extends BasePlatformTestCase {

    private static CompiledGraph compiled(String database, String location) throws Exception {
        ProjectConfig config = new ProjectConfig();
        set(config, "defaultDatabase", database);
        set(config, "defaultLocation", location);
        CompiledGraph graph = new CompiledGraph();
        set(graph, "projectConfig", config);
        return graph;
    }

    private static void set(Object target, String field, Object value) throws Exception {
        Field declared = target.getClass().getDeclaredField(field);
        declared.setAccessible(true);
        declared.set(target, value);
    }

    public void testTheLastCompilationWinsAndTheSettingsFileFillsItsGaps() throws Exception {
        myFixture.addFileToProject("workflow_settings.yaml", "defaultProject: from-settings\ndefaultLocation: US\n");
        PsiFile file = myFixture.addFileToProject("definitions/a.sqlx", "SELECT 1");

        DataformProjectDefaults defaults = DataformProjectDefaults.of(getProject(), file.getVirtualFile(),
                compiled("from-compiled", null));

        assertEquals("from-compiled", defaults.database());
        assertEquals("what the compilation left out comes from the file", "US", defaults.location());
    }

    public void testBeforeTheFirstCompilationTheSettingsFileAnswers() {
        myFixture.addFileToProject("workflow_settings.yaml", "defaultProject: from-settings\n");
        PsiFile file = myFixture.addFileToProject("definitions/a.sqlx", "SELECT 1");

        assertEquals("from-settings", DataformProjectDefaults.of(getProject(), file.getVirtualFile(), null).database());
    }

    public void testWithoutASettingsFileTheLastCompilationAnswers() throws Exception {
        PsiFile file = myFixture.addFileToProject("definitions/a.sqlx", "SELECT 1");

        DataformProjectDefaults defaults = DataformProjectDefaults.of(getProject(), file.getVirtualFile(),
                compiled("from-compiled", null));

        assertEquals("from-compiled", defaults.database());
        assertNull(defaults.location());
    }
}
