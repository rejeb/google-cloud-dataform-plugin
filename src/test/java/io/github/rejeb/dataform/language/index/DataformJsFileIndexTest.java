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
package io.github.rejeb.dataform.language.index;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.util.DataformProjectLayout;

/**
 * Dataform turns into globals only the JavaScript files placed directly in the {@code includes}
 * directory of a project, so the index and the layout must agree on that.
 */
public class DataformJsFileIndexTest extends BasePlatformTestCase {

    private static final String HELPER = "function top_value(name) { return name; }\nmodule.exports = { top_value };\n";

    public void testOnlyTheFilesDirectlyInIncludesAreIncludes() {
        myFixture.addFileToProject("workflow_settings.yaml", "defaultProject: p\n");
        myFixture.addFileToProject("includes/helpers.js", HELPER);
        myFixture.addFileToProject("includes/sub/nested.js", HELPER);

        assertTrue(DataformJsFileIndex.getAllExports(getProject()).containsKey("helpers"));
        assertFalse("a file below a subdirectory of includes is no global",
                DataformJsFileIndex.getAllExports(getProject()).containsKey("nested"));
    }

    public void testTheIndexAndTheLayoutNameTheSameIncludes() {
        myFixture.addFileToProject("workflow_settings.yaml", "defaultProject: p\n");
        myFixture.addFileToProject("includes/helpers.js", HELPER);
        myFixture.addFileToProject("includes/sub/nested.js", HELPER);
        var definition = myFixture.addFileToProject("definitions/a.sqlx", "SELECT 1");

        assertEquals(DataformProjectLayout.includeNames(definition.getVirtualFile()),
                DataformJsFileIndex.getAllExports(getProject()).keySet());
    }
}
