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

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public class DataformCompilationSourceChangeTest extends BasePlatformTestCase {

    public void testSqlxUnderDefinitionsIsDetected() {
        VirtualFile file = myFixture.addFileToProject("definitions/table.sqlx", "SELECT 1").getVirtualFile();
        VirtualFile root = rootOf(file, "definitions");

        assertTrue(DataformCompilationServiceImpl.isModifiedAfter(root, file.getTimeStamp() - 1));
        assertFalse(DataformCompilationServiceImpl.isModifiedAfter(root, file.getTimeStamp()));
    }

    public void testTypeScriptIncludeIsDetected() {
        VirtualFile file = myFixture.addFileToProject("includes/helpers.ts", "export const a = 1;").getVirtualFile();
        VirtualFile root = rootOf(file, "includes");

        assertTrue(DataformCompilationServiceImpl.isModifiedAfter(root, file.getTimeStamp() - 1));
    }

    public void testWorkflowSettingsIsDetected() {
        VirtualFile file = myFixture.addFileToProject("workflow_settings.yaml",
                "defaultProject: p").getVirtualFile();
        VirtualFile root = file.getParent();

        assertTrue(DataformCompilationServiceImpl.isModifiedAfter(root, file.getTimeStamp() - 1));
    }

    public void testNodeModulesIsIgnored() {
        VirtualFile file = myFixture.addFileToProject("node_modules/pkg/index.js", "module.exports = {};")
                .getVirtualFile();
        VirtualFile root = rootOf(file, "node_modules");

        assertFalse(DataformCompilationServiceImpl.isModifiedAfter(root, file.getTimeStamp() - 1));
    }

    public void testUnrelatedFileIsIgnored() {
        VirtualFile file = myFixture.addFileToProject("definitions/README.md", "doc").getVirtualFile();
        VirtualFile root = rootOf(file, "definitions");

        assertFalse(DataformCompilationServiceImpl.isModifiedAfter(root, file.getTimeStamp() - 1));
    }

    private static VirtualFile rootOf(VirtualFile file, String topLevelDirectory) {
        VirtualFile current = file.getParent();
        while (current != null && !topLevelDirectory.equals(current.getName())) {
            current = current.getParent();
        }
        assertNotNull(current);
        return current.getParent();
    }
}
