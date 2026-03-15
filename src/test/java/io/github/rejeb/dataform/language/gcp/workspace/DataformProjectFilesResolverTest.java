/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.rejeb.dataform.language.gcp.workspace;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.List;

public class DataformProjectFilesResolverTest extends BasePlatformTestCase {

    public void testResolverFindsFilesInDefinitions() {
        myFixture.addFileToProject("definitions/table_a.sqlx", "SELECT 1");
        myFixture.addFileToProject("definitions/table_b.sqlx", "SELECT 2");

        List<String> paths = DataformProjectFilesResolver.resolve(getProject());

        assertTrue(paths.contains("definitions/table_a.sqlx"));
        assertTrue(paths.contains("definitions/table_b.sqlx"));
    }

    public void testResolverFindsFilesInIncludes() {
        myFixture.addFileToProject("includes/helpers.js", "function foo() {}");

        List<String> paths = DataformProjectFilesResolver.resolve(getProject());

        assertTrue(paths.contains("includes/helpers.js"));
    }

    public void testResolverFindsWorkflowSettingsYaml() {
        myFixture.addFileToProject("workflow_settings.yaml", "defaultProject: test");

        List<String> paths = DataformProjectFilesResolver.resolve(getProject());

        assertTrue(paths.contains("workflow_settings.yaml"));
    }

    public void testResolverFindsDataformJson() {
        myFixture.addFileToProject("dataform.json", "{}");

        List<String> paths = DataformProjectFilesResolver.resolve(getProject());

        assertTrue(paths.contains("dataform.json"));
    }

    public void testResolverIgnoresFilesOutsideWatchedDirectories() {
        myFixture.addFileToProject("node_modules/some_lib.js", "");
        myFixture.addFileToProject(".dataform/compiled.json", "{}");

        List<String> paths = DataformProjectFilesResolver.resolve(getProject());

        assertFalse(paths.stream().anyMatch(p -> p.contains("node_modules")));
        assertFalse(paths.stream().anyMatch(p -> p.contains(".dataform")));
    }

    public void testResolverReturnsEmptyWhenNoDataformFilesPresent() {
        List<String> paths = DataformProjectFilesResolver.resolve(getProject());

        assertTrue(paths.isEmpty());
    }
}
