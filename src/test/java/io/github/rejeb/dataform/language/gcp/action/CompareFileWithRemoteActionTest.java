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
package io.github.rejeb.dataform.language.gcp.action;

import com.intellij.diff.DiffManager;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.gcp.service.DataformGcpService;
import io.github.rejeb.dataform.language.gcp.settings.DataformRepositoryConfig;
import io.github.rejeb.dataform.language.gcp.settings.GcpRepositorySettings;
import io.github.rejeb.dataform.language.gcp.workspace.MainBranchSource;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CompareFileWithRemoteActionTest extends BasePlatformTestCase {

    private static final DataformRepositoryConfig SALES =
            new DataformRepositoryConfig("sales-id", "Sales", "p", "sales", "europe-west1");

    private DataformGcpService service;
    private DiffManager diffs;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        resetSettings();
        service = mock(DataformGcpService.class);
        when(service.mainBranchSource()).thenReturn(MainBranchSource.DATAFORM);
        ServiceContainerUtil.replaceService(getProject(), DataformGcpService.class, service, getTestRootDisposable());
        diffs = mock(DiffManager.class);
        ServiceContainerUtil.replaceService(ApplicationManager.getApplication(), DiffManager.class, diffs,
                getTestRootDisposable());
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            resetSettings();
        } finally {
            super.tearDown();
        }
    }

    public void testTheRemoteSideHoldsTheMainBranchVersionOfTheFile() {
        when(service.getFileContent(isNull(), eq("definitions/a.sqlx"))).thenReturn("SELECT 2");
        VirtualFile file = myFixture.addFileToProject("definitions/a.sqlx", "SELECT 1").getVirtualFile();

        SimpleDiffRequest request = compare(file);

        assertEquals("SELECT 1", text(request, 0));
        assertEquals("SELECT 2", text(request, 1));
        assertEquals(List.of("Local", "Remote (GCP)"), request.getContentTitles());
    }

    public void testTheRemoteSideHoldsTheVersionOfTheSelectedWorkspace() {
        GcpRepositorySettings settings = GcpRepositorySettings.getInstance(getProject());
        settings.saveAllConfigs(List.of(SALES));
        settings.setActiveRepositoryId(SALES.repositoryConfigId());
        settings.setSelectedWorkspaceId("dev");
        when(service.getFileContent("dev", "definitions/a.sqlx")).thenReturn("SELECT 3");
        VirtualFile file = myFixture.addFileToProject("definitions/a.sqlx", "SELECT 1").getVirtualFile();

        assertEquals("SELECT 3", text(compare(file), 1));
    }

    private SimpleDiffRequest compare(VirtualFile file) {
        CompareFileWithRemoteAction action = new CompareFileWithRemoteAction();
        action.actionPerformed(TestActionEvent.createTestEvent(action, SimpleDataContext.builder()
                .add(CommonDataKeys.PROJECT, getProject())
                .add(CommonDataKeys.VIRTUAL_FILE, file)
                .build()));
        PlatformTestUtil.waitWithEventsDispatching("no diff was shown",
                () -> !mockingDetails(diffs).getInvocations().isEmpty(), 10);
        ArgumentCaptor<DiffRequest> request = ArgumentCaptor.forClass(DiffRequest.class);
        verify(diffs).showDiff(any(), request.capture());
        return (SimpleDiffRequest) request.getValue();
    }

    private static String text(SimpleDiffRequest request, int side) {
        return ((DocumentContent) request.getContents().get(side)).getDocument().getText();
    }

    private void resetSettings() {
        GcpRepositorySettings settings = GcpRepositorySettings.getInstance(getProject());
        settings.saveAllConfigs(List.of());
        settings.setActiveRepositoryId(null);
        settings.setSelectedWorkspaceId(null);
    }
}
