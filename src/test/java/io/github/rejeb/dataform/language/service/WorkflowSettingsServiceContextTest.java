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

import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.Map;

public class WorkflowSettingsServiceContextTest extends BasePlatformTestCase {

    public void testContextLookupResolvesTheNearestSettingsFile() {
        myFixture.addFileToProject("a/workflow_settings.yaml", "defaultProject: project-a\n");
        myFixture.addFileToProject("b/workflow_settings.yaml", "defaultProject: project-b\n");
        PsiFile inB = myFixture.addFileToProject("b/definitions/mart.sqlx", "SELECT 1");

        WorkflowSettingsService service = WorkflowSettingsService.getInstance(getProject());
        Map<String, WorkflowSettingsProperty> properties =
                service.getWorkflowProperties(inB.getVirtualFile());

        WorkflowSettingsProperty dataform = properties.get("dataform");
        assertNotNull("settings of project B must be found from a file of project B", dataform);
        WorkflowSettingsProperty projectConfig = dataform.children().get("projectConfig");
        assertNotNull(projectConfig);
        assertEquals("project-b", projectConfig.children().get("defaultProject").value());
    }

    public void testContextLookupDoesNotServeAnotherProjectsCachedFile() {
        myFixture.addFileToProject("a/workflow_settings.yaml", "defaultProject: project-a\n");
        PsiFile inA = myFixture.addFileToProject("a/definitions/one.sqlx", "SELECT 1");
        PsiFile outside = myFixture.addFileToProject("plain/app.js", "const x = 1;");

        WorkflowSettingsService service = WorkflowSettingsService.getInstance(getProject());
        assertNotNull(service.findWorkflowSettingsVirtualFile(inA.getVirtualFile()));

        VirtualFile forOutside = service.findWorkflowSettingsVirtualFile(outside.getVirtualFile());
        assertTrue("a file outside project A must not inherit A's cached settings file",
                forOutside == null || VfsUtilCore.isAncestor(forOutside.getParent(),
                        outside.getVirtualFile(), false));
    }
}
