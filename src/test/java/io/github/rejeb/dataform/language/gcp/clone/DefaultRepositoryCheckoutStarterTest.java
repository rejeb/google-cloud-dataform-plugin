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

import com.intellij.openapi.util.io.NioFiles;
import com.intellij.openapi.vcs.CheckoutProvider;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.gcp.workspace.DataformRepository;
import io.github.rejeb.dataform.language.gcp.workspace.repository.WorkspaceRepository;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class DefaultRepositoryCheckoutStarterTest extends BasePlatformTestCase {

    private final List<Path> opened = new CopyOnWriteArrayList<>();
    private final List<String> completed = new CopyOnWriteArrayList<>();
    private final CheckoutProvider.Listener listener = new CheckoutProvider.Listener() {
        @Override
        public void directoryCheckedOut(File directory, VcsKey vcs) {
        }

        @Override
        public void checkoutCompleted() {
            completed.add("completed");
        }
    };
    private WorkspaceRepository repository;
    private DefaultRepositoryCheckoutStarter starter;
    private Path root;
    private Path directory;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        repository = mock(WorkspaceRepository.class);
        root = Files.createTempDirectory("dataform-starter");
        directory = root.resolve("projects").resolve("analytics-core");
        starter = new DefaultRepositoryCheckoutStarter(getProject(), repository, new PendingRepositoryLinksImpl(), opened::add);
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            NioFiles.deleteRecursively(root);
        } finally {
            super.tearDown();
        }
    }

    private void waitForCompletion() {
        PlatformTestUtil.waitWithEventsDispatching("the checkout did not complete", () -> !completed.isEmpty(), 60);
    }

    private static void git(Path directory, String... arguments) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of("git"));
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(output, 0, process.waitFor());
    }

    public void testAWorkspaceIsDownloadedAndOpened() {
        when(repository.readAllFiles("acme", "europe-west1", "analytics-core", "dev-you"))
                .thenReturn(Map.of("definitions/orders.sqlx", "select 1"));

        starter.start(new RepositoryCheckoutRequest("acme", "europe-west1",
                new DataformRepository("analytics-core", null, null, ""),
                RepositorySource.WORKSPACE, "dev-you", directory), listener);
        waitForCompletion();

        assertEquals(List.of(directory), opened);
        assertTrue(Files.exists(directory.resolve("definitions/orders.sqlx")));
    }

    public void testAGitRemoteIsCloned() throws IOException, InterruptedException {
        Path origin = root.resolve("origin");
        Files.createDirectories(origin);
        git(origin, "init", "-b", "main");
        Files.writeString(origin.resolve("workflow_settings.yaml"), "defaultProject: acme\n");
        git(origin, "add", ".");
        git(origin, "-c", "user.name=t", "-c", "user.email=t@example.com", "commit", "-m", "first");

        starter.start(new RepositoryCheckoutRequest("acme", "europe-west1",
                new DataformRepository("analytics-core", origin.toUri().toString(), "main", ""),
                RepositorySource.GIT_REMOTE, null, directory), listener);
        waitForCompletion();

        assertTrue(Files.exists(directory.resolve(".git")));
        assertTrue(Files.exists(directory.resolve("workflow_settings.yaml")));
        verifyNoInteractions(repository);
    }
}
