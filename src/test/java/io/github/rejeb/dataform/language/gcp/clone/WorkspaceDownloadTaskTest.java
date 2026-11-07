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
package io.github.rejeb.dataform.language.gcp.clone;

import com.intellij.notification.Notification;
import com.intellij.notification.Notifications;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.vcs.CheckoutProvider;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.wm.impl.welcomeScreen.cloneableProjects.CloneableProjectsService.CloneStatus;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.gcp.common.GcpApiException;
import io.github.rejeb.dataform.language.gcp.workspace.DataformRepository;
import io.github.rejeb.dataform.language.gcp.workspace.repository.WorkspaceRepository;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.NioFiles;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class WorkspaceDownloadTaskTest extends BasePlatformTestCase {

    private static final DataformRepository REPOSITORY = new DataformRepository("finance-marts", null, null, "");

    private final List<String> listenerCalls = new ArrayList<>();
    private final List<Notification> notifications = new ArrayList<>();
    private final CheckoutProvider.Listener listener = new CheckoutProvider.Listener() {
        @Override
        public void directoryCheckedOut(File directory, VcsKey vcs) {
            listenerCalls.add("directoryCheckedOut");
        }

        @Override
        public void checkoutCompleted() {
            listenerCalls.add("checkoutCompleted");
        }
    };
    private WorkspaceRepository repository;
    private Path directory;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        repository = mock(WorkspaceRepository.class);
        directory = Files.createTempDirectory("dataform-download-task").resolve("finance-marts");
        getProject().getMessageBus().connect(getTestRootDisposable())
                .subscribe(Notifications.TOPIC, new Notifications() {
                    @Override
                    public void notify(Notification notification) {
                        notifications.add(notification);
                    }
                });
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            FileUtil.delete(directory.getParent());
        } finally {
            super.tearDown();
        }
    }

    private WorkspaceDownloadTask task() {
        RepositoryCheckoutRequest request = new RepositoryCheckoutRequest("acme", "europe-west1", REPOSITORY,
                RepositorySource.WORKSPACE, "dev-you", directory);
        WorkspaceDownload download = new WorkspaceDownload(repository, new PendingRepositoryLinksImpl(), dir -> {
        });
        return new WorkspaceDownloadTask(download, request, listener, getProject());
    }

    public void testASuccessfulDownloadCompletesTheCheckout() {
        when(repository.readAllFiles("acme", "europe-west1", "finance-marts", "dev-you"))
                .thenReturn(Map.of("workflow_settings.yaml", ""));

        CloneStatus status = task().run(new EmptyProgressIndicator());

        assertEquals(CloneStatus.SUCCESS, status);
        assertEquals(List.of("checkoutCompleted"), listenerCalls);
    }

    public void testAFailedDownloadTellsTheUserWhy() {
        when(repository.readAllFiles("acme", "europe-west1", "finance-marts", "dev-you"))
                .thenThrow(new GcpApiException("Permission denied on the workspace", new RuntimeException()));

        CloneStatus status = task().run(new EmptyProgressIndicator());
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();

        assertEquals(CloneStatus.FAILURE, status);
        assertEmpty(listenerCalls);
        assertEquals(1, notifications.size());
        assertTrue(notifications.getFirst().getContent(), notifications.getFirst().getContent().contains("Permission denied on the workspace"));
    }

    public void testAFileThatCannotBeWrittenIsAFailureNotACrash() {
        when(repository.readAllFiles("acme", "europe-west1", "finance-marts", "dev-you"))
                .thenReturn(Map.of("../../evil.txt", ""));

        CloneStatus status = task().run(new EmptyProgressIndicator());

        assertEquals(CloneStatus.FAILURE, status);
    }
}
