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
package io.github.rejeb.dataform.language.util;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.Set;

public class DataformProjectLayoutTest extends BasePlatformTestCase {

    public void testFindWorkflowSettingsWalksUpFromNestedFile() {
        myFixture.addFileToProject("repo/dataform/workflow_settings.yaml", "defaultProject: p\n");
        PsiFile file = myFixture.addFileToProject("repo/dataform/definitions/mart.sqlx", "SELECT 1");

        VirtualFile found = DataformProjectLayout.findWorkflowSettings(file.getVirtualFile());

        assertNotNull(found);
        assertEquals("workflow_settings.yaml", found.getName());
        assertEquals("dataform", found.getParent().getName());
    }

    public void testFindWorkflowSettingsReturnsNullWithoutSettingsFile() {
        PsiFile file = myFixture.addFileToProject("plain/src/app.js", "const a = 1;");

        assertNull(DataformProjectLayout.findWorkflowSettings(file.getVirtualFile()));
    }

    public void testIncludeNamesListsJsFilesOfNearestIncludesDirectory() {
        myFixture.addFileToProject("proj/includes/helpers.js", "module.exports = {};");
        myFixture.addFileToProject("proj/includes/notes.txt", "not js");
        PsiFile file = myFixture.addFileToProject("proj/definitions/mart.sqlx", "SELECT 1");

        assertEquals(Set.of("helpers"), DataformProjectLayout.includeNames(file.getVirtualFile()));
    }

    public void testIsInDataformProjectAcceptsDefinitionsPathWithoutSettingsFile() {
        PsiFile file = myFixture.addFileToProject("definitions/mart.js", "publish(\"m\");");

        assertTrue(DataformProjectLayout.isInDataformProject(file.getVirtualFile()));
    }

    public void testIsInDataformProjectRejectsPlainJsProject() {
        PsiFile file = myFixture.addFileToProject("plain/src/app.js", "const a = 1;");

        assertFalse(DataformProjectLayout.isInDataformProject(file.getVirtualFile()));
    }
}
