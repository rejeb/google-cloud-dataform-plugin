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
package io.github.rejeb.dataform.language.gcp.gitremote;

import com.intellij.openapi.util.io.NioFiles;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.gcp.workspace.DataformRepository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class GitRemoteBranchesImplTest extends BasePlatformTestCase {

    private Path root;
    private Path origin;
    private Path clone;

    @Override
    protected boolean runInDispatchThread() {
        return false;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        root = Files.createTempDirectory("dataform-git-remote");
        origin = root.resolve("origin");
        clone = root.resolve("clone");
        Files.createDirectories(origin.resolve("definitions"));
        git(origin, "init", "-b", "main");
        Files.writeString(origin.resolve("workflow_settings.yaml"), "defaultProject: acme\n");
        Files.writeString(origin.resolve("definitions/orders.sqlx"), "SELECT 'é'\n");
        commit("first");
        git(root, "clone", origin.toUri().toString(), "clone");
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            NioFiles.deleteRecursively(root);
        } finally {
            super.tearDown();
        }
    }

    private void commit(String message) throws Exception {
        git(origin, "add", ".");
        git(origin, "-c", "user.name=t", "-c", "user.email=t@example.com", "commit", "-m", message);
    }

    private static String git(Path directory, String... arguments) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of("git"));
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(output, 0, process.waitFor());
        return output.trim();
    }

    private DataformRepository repository(String url, String defaultBranch) {
        return new DataformRepository("analytics", url, defaultBranch, "");
    }

    private DataformRepository linked() {
        return repository(origin.toUri().toString(), "main");
    }

    private GitRemoteBranches in(Path directory) {
        return new GitRemoteBranchesImpl(getProject(), () -> directory);
    }

    private GitRemoteBranch branch() {
        GitRemoteBranch branch = in(clone).find(linked());
        assertNotNull(branch);
        return branch;
    }

    public void testTheDefaultBranchOfACloneOfTheRemoteIsFound() {
        GitRemoteBranch branch = branch();

        assertEquals("main", branch.name());
        assertEquals("origin/main", branch.trackingName());
    }

    public void testTheRemoteIsFoundWhateverItsName() throws Exception {
        git(clone, "remote", "rename", "origin", "upstream");

        assertEquals("upstream/main", branch().trackingName());
    }

    public void testTheRemoteMatchingTheRepositoryIsPickedAmongSeveral() throws Exception {
        git(clone, "remote", "rename", "origin", "upstream");
        git(clone, "remote", "add", "another", "https://example.com/another.git");

        assertEquals("upstream/main", branch().trackingName());
    }

    public void testADirectoryThatIsNotARepositoryHasNoBranch() throws Exception {
        Path plain = Files.createDirectory(root.resolve("plain"));

        assertNull(in(plain).find(linked()));
    }

    public void testACloneOfAnotherRemoteHasNoBranch() {
        assertNull(in(clone).find(repository("https://example.com/another.git", "main")));
    }

    public void testABranchTheCloneNeverFetchedIsNotFound() throws Exception {
        git(origin, "branch", "release");

        assertNull(in(clone).find(repository(origin.toUri().toString(), "release")));
    }

    public void testARepositoryWithoutADefaultBranchHasNoBranch() {
        assertNull(in(clone).find(repository(origin.toUri().toString(), null)));
    }

    public void testAProjectWithoutADirectoryHasNoBranch() {
        assertNull(in(null).find(linked()));
    }

    public void testFilesAreListedFromTheRemoteBranchNotTheWorkingTree() throws Exception {
        Files.writeString(clone.resolve("definitions/untracked.sqlx"), "SELECT 2\n");
        Files.delete(clone.resolve("workflow_settings.yaml"));

        assertEquals(List.of("definitions/orders.sqlx", "workflow_settings.yaml"), branch().listFiles());
    }

    public void testANonAsciiPathIsListedAsIs() throws Exception {
        Files.writeString(origin.resolve("definitions/été.sqlx"), "SELECT 3\n");
        commit("accent");
        git(clone, "fetch");

        assertContainsElements(branch().listFiles(), "definitions/été.sqlx");
    }

    public void testAFileIsReadFromTheRemoteBranchNotTheWorkingTree() throws Exception {
        Files.writeString(clone.resolve("definitions/orders.sqlx"), "SELECT 'local'\n");

        assertEquals("SELECT 'é'\n", branch().readFile("definitions/orders.sqlx"));
    }

    public void testAFileTheBranchDoesNotHaveFailsWithItsName() {
        GitRemoteBranch branch = branch();

        assertThrows(GitRemoteBranchException.class, "definitions/missing.sqlx",
                () -> branch.readFile("definitions/missing.sqlx"));
    }

    public void testFetchBringsTheBranchUpToDate() throws Exception {
        GitRemoteBranch branch = branch();
        Files.writeString(origin.resolve("definitions/customers.sqlx"), "SELECT 4\n");
        commit("second");
        assertDoesntContain(branch.listFiles(), "definitions/customers.sqlx");

        branch.fetch();

        assertContainsElements(branch.listFiles(), "definitions/customers.sqlx");
    }

    public void testFetchLeavesTheWorkingTreeAndTheLocalBranchAlone() throws Exception {
        String localHead = git(clone, "rev-parse", "HEAD");
        Files.writeString(origin.resolve("definitions/customers.sqlx"), "SELECT 4\n");
        commit("second");

        branch().fetch();

        assertEquals(localHead, git(clone, "rev-parse", "HEAD"));
        assertEquals("* main", git(clone, "branch", "--list"));
        assertFalse(Files.exists(clone.resolve("definitions/customers.sqlx")));
    }

    public void testAFetchThatCannotReachTheRemoteFailsAndKeepsTheLastFiles() throws Exception {
        GitRemoteBranch branch = branch();
        NioFiles.deleteRecursively(origin);

        assertThrows(GitRemoteBranchException.class, "origin/main", branch::fetch);

        assertEquals(List.of("definitions/orders.sqlx", "workflow_settings.yaml"), branch.listFiles());
    }

    public void testTheServiceIsRegisteredAndALightProjectHasNoClone() {
        GitRemoteBranches service = getProject().getService(GitRemoteBranches.class);

        assertInstanceOf(service, GitRemoteBranchesImpl.class);
        assertNull(service.find(linked()));
    }

    private void commitInTheClone() throws Exception {
        git(clone, "add", ".");
        git(clone, "-c", "user.name=t", "-c", "user.email=t@example.com", "commit", "-m", "local");
    }

    public void testAFileIsReadFromTheRemoteBranchNotTheLocalBranch() throws Exception {
        Files.writeString(clone.resolve("definitions/orders.sqlx"), "SELECT 'local'\n");
        commitInTheClone();

        assertEquals("SELECT 'é'\n", branch().readFile("definitions/orders.sqlx"));
    }

    public void testFilesAreListedFromTheRemoteBranchNotTheLocalBranch() throws Exception {
        Files.writeString(clone.resolve("definitions/local.sqlx"), "SELECT 2\n");
        commitInTheClone();

        assertEquals(List.of("definitions/orders.sqlx", "workflow_settings.yaml"), branch().listFiles());
    }

    public void testFetchUpdatesTheBranchInACloneTrackingOnlyOthers() throws Exception {
        GitRemoteBranch branch = branch();
        git(clone, "config", "remote.origin.fetch", "+refs/heads/other:refs/remotes/origin/other");
        Files.writeString(origin.resolve("definitions/customers.sqlx"), "SELECT 4\n");
        commit("second");

        branch.fetch();

        assertContainsElements(branch.listFiles(), "definitions/customers.sqlx");
    }
}
