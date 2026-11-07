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
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.openapi.vcs.CheckoutProvider;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.wm.impl.welcomeScreen.cloneableProjects.CloneableProjectsService;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.gcp.workspace.DataformRepository;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Runs the real Git executable against a repository in a temporary directory.
 */
public class GitRemoteCheckoutTest extends BasePlatformTestCase {

    private final PendingRepositoryLinks links = new PendingRepositoryLinksImpl();
    private final List<String> events = new CopyOnWriteArrayList<>();
    private final List<Notification> notifications = new ArrayList<>();
    private final CheckoutProvider.Listener listener = new CheckoutProvider.Listener() {
        @Override
        public void directoryCheckedOut(File directory, VcsKey vcs) {
            RepositoryLink link = links.take(directory.getPath());
            events.add("opened on " + head(directory.toPath()) + " linked to " + (link == null ? null : link.repositoryId()));
        }

        @Override
        public void checkoutCompleted() {
            events.add("completed");
        }
    };
    private Path root;
    private Path origin;
    private Path target;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        root = Files.createTempDirectory("dataform-git");
        origin = root.resolve("origin");
        target = root.resolve("projects").resolve("analytics-core");
        Files.createDirectories(origin);
        git(origin, "init", "-b", "main");
        Files.writeString(origin.resolve("workflow_settings.yaml"), "defaultProject: acme\n");
        git(origin, "add", ".");
        git(origin, "-c", "user.name=t", "-c", "user.email=t@example.com", "commit", "-m", "first");
        git(origin, "branch", "develop");
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
            NioFiles.deleteRecursively(root);
        } finally {
            super.tearDown();
        }
    }

    private static void git(Path directory, String... arguments) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of("git"));
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(output, 0, process.waitFor());
    }

    private static String head(Path directory) {
        try {
            return Files.readString(directory.resolve(".git/HEAD")).trim().replace("ref: refs/heads/", "");
        } catch (IOException e) {
            return e.toString();
        }
    }

    private void checkout(String url, String defaultBranch) {
        RepositoryCheckoutRequest request = new RepositoryCheckoutRequest("acme", "europe-west1",
                new DataformRepository("analytics-core", url, defaultBranch, ""),
                RepositorySource.GIT_REMOTE, null, target);
        GitRemoteCheckout.start(getProject(), request, listener, links);
        PlatformTestUtil.waitWithEventsDispatching("the clone did not end",
                () -> !CloneableProjectsService.getInstance().isCloneActive(), 60);
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
    }

    private String originUrl() {
        return origin.toUri().toString();
    }

    public void testTheRemoteIsClonedOnItsDefaultBranchAndLinkedBeforeTheProjectOpens() {
        checkout(originUrl(), "develop");

        assertEquals(List.of("opened on develop linked to analytics-core", "completed"), events);
        assertTrue(Files.isRegularFile(target.resolve("workflow_settings.yaml")));
    }

    public void testWithoutADefaultBranchTheCloneStaysOnTheBranchOfTheRemote() {
        checkout(originUrl(), null);

        assertEquals(List.of("opened on main linked to analytics-core", "completed"), events);
    }

    public void testADefaultBranchMissingFromTheRemoteStillOpensTheProjectAndWarns() {
        checkout(originUrl(), "no-such-branch");

        assertEquals(List.of("opened on main linked to analytics-core", "completed"), events);
        assertEquals(1, notifications.size());
        assertTrue(notifications.getFirst().getContent(), notifications.getFirst().getContent().contains("no-such-branch"));
    }

    public void testABranchThatLooksLikeAnOptionIsNeverPassedToGit() {
        checkout(originUrl(), "--detach");

        assertEquals(List.of("opened on main linked to analytics-core", "completed"), events);
    }

    public void testARemoteThatLooksLikeAnOptionIsRefused() {
        checkout("--upload-pack=touch evil", "main");

        assertEmpty(events);
        assertFalse(Files.exists(target));
        assertEquals(1, notifications.size());
    }

    public void testAFailedCloneOpensNothingAndLinksNothing() {
        checkout(root.resolve("no-such-remote").toUri().toString(), "main");

        assertEmpty(events);
        assertNull(links.take(target.toString()));
    }
}
