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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.gcp.workspace.DataformRepository;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class GitConfigLocalClonesTest extends BasePlatformTestCase {

    private static final String URL = "https://github.com/acme/core.git";
    private static final DataformRepository LINKED = new DataformRepository("analytics", URL, "main", "");

    private Path root;
    private Path project;

    @Override
    protected boolean runInDispatchThread() {
        return false;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        root = Files.createTempDirectory("dataform-local-clone");
        project = root.resolve("project");
        Files.createDirectories(project);
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            NioFiles.deleteRecursively(root);
        } finally {
            super.tearDown();
        }
    }

    private static String git(Path directory, String... arguments) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of("git"));
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(output, 0, process.waitFor());
        return output.trim();
    }

    private boolean isClone(Path directory) {
        return new GitConfigLocalClones(() -> directory).isCloneOf(LINKED);
    }

    private void writeConfig(String content) throws IOException {
        Files.createDirectories(project.resolve(".git"));
        Files.writeString(project.resolve(".git/config"), content);
    }

    private void commitOnce() throws Exception {
        git(project, "-c", "user.name=t", "-c", "user.email=t@example.com", "commit", "--allow-empty", "-m", "first");
    }

    private List<String> recordFileEvents() {
        List<String> paths = new CopyOnWriteArrayList<>();
        ApplicationManager.getApplication().getMessageBus().connect(getTestRootDisposable())
                .subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
                    @Override
                    public void after(@NotNull List<? extends VFileEvent> events) {
                        events.forEach(event -> paths.add(event.getPath()));
                    }
                });
        return paths;
    }

    private static VirtualFile known(Path directory) {
        VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(directory);
        assertNotNull(file);
        file.getChildren();
        return file;
    }

    public void testAProjectWithTheRemoteIsAClone() throws Exception {
        git(project, "init", "-b", "main");
        git(project, "remote", "add", "origin", URL);

        assertTrue(isClone(project));
    }

    public void testTheProtocolOfTheRemoteDoesNotMatter() throws Exception {
        git(project, "init", "-b", "main");
        git(project, "remote", "add", "origin", "git@github.com:acme/core.git");

        assertTrue(isClone(project));
    }

    public void testTheNameOfTheRemoteDoesNotMatter() throws Exception {
        git(project, "init", "-b", "main");
        git(project, "remote", "add", "origin", "https://github.com/acme/fork.git");
        git(project, "remote", "add", "upstream", URL);

        assertTrue(isClone(project));
    }

    public void testAnotherRemoteIsNotAClone() throws Exception {
        git(project, "init", "-b", "main");
        git(project, "remote", "add", "origin", "https://github.com/acme/fork.git");

        assertFalse(isClone(project));
    }

    public void testNoGitDirectoryIsNotAClone() {
        assertFalse(isClone(project));
    }

    public void testNoProjectDirectoryIsNotAClone() {
        assertFalse(isClone(null));
    }

    public void testAGitDirectoryWithoutConfigIsNotAClone() throws Exception {
        Files.createDirectories(project.resolve(".git"));

        assertFalse(isClone(project));
    }

    public void testASubdirectoryOfTheCloneIsAClone() throws Exception {
        git(project, "init", "-b", "main");
        git(project, "remote", "add", "origin", URL);
        Path subdirectory = project.resolve("dataform/core");
        Files.createDirectories(subdirectory);

        assertTrue(isClone(subdirectory));
    }

    public void testANearerRepositoryHidesTheClone() throws Exception {
        git(project, "init", "-b", "main");
        git(project, "remote", "add", "origin", URL);
        Path nested = project.resolve("nested");
        Files.createDirectories(nested);
        git(nested, "init", "-b", "main");

        assertFalse(isClone(nested));
    }

    public void testAWorktreeOfTheCloneIsAClone() throws Exception {
        git(project, "init", "-b", "main");
        git(project, "remote", "add", "origin", URL);
        commitOnce();
        git(project, "worktree", "add", "-b", "feature", "../linked");

        assertTrue(isClone(root.resolve("linked")));
    }

    public void testAGitDirectoryKeptElsewhereIsFollowed() throws Exception {
        git(project, "init", "-b", "main", "--separate-git-dir=../elsewhere");
        git(project, "remote", "add", "origin", URL);

        assertTrue(isClone(project));
    }

    public void testAMalformedGitFileIsNotAClone() throws Exception {
        Files.writeString(project.resolve(".git"), "not a git link");

        assertFalse(isClone(project));
    }

    public void testABadByteElsewhereDoesNotHideTheRemote() throws Exception {
        writeConfig("[user]\n\tname = X\n[remote \"origin\"]\n\turl = " + URL + "\n");
        byte[] config = Files.readAllBytes(project.resolve(".git/config"));
        config[config.length - URL.length() - 30] = (byte) 0xE9;
        Files.write(project.resolve(".git/config"), config);

        assertTrue(isClone(project));
    }

    public void testAUrlOutsideARemoteIsNotAClone() throws Exception {
        git(project, "init", "-b", "main");
        git(project, "config", "branch.main.url", URL);
        git(project, "config", "url." + URL + ".insteadOf", "x");

        assertFalse(isClone(project));
    }

    public void testAKeyEndingInUrlIsNotAClone() throws Exception {
        git(project, "init", "-b", "main");
        git(project, "config", "remote.origin.pushurl", URL);

        assertFalse(isClone(project));
    }

    public void testAQuotedValueCaseAndCommentsAreRead() throws Exception {
        writeConfig("""
                [REMOTE "origin"]
                \t# url = https://github.com/acme/fork.git
                \tURL = "https://github.com/acme/core.git" ; the one that counts
                """);

        assertTrue(isClone(project));
    }

    public void testACommentAfterAnUnquotedValueIsDropped() throws Exception {
        writeConfig("""
                [remote "origin"]
                \turl = https://github.com/acme/core.git # the one that counts
                """);

        assertTrue(isClone(project));
    }

    public void testASectionAfterTheRemoteEndsIt() throws Exception {
        writeConfig("""
                [remote "origin"]
                \tfetch = +refs/heads/*:refs/remotes/origin/*
                [other]
                \turl = https://github.com/acme/core.git
                """);

        assertFalse(isClone(project));
    }

    public void testARepositoryWithoutRemoteIsNeverAClone() throws Exception {
        git(project, "init", "-b", "main");
        git(project, "remote", "add", "origin", URL);

        assertFalse(new GitConfigLocalClones(() -> project)
                .isCloneOf(new DataformRepository("analytics", null, null, "")));
    }

    public void testAGitDirectoryAppearingReachesFileListeners() throws Exception {
        VirtualFile directory = known(project);
        List<String> changed = recordFileEvents();

        git(project, "init", "-b", "main");
        VfsUtil.markDirtyAndRefresh(false, true, true, directory);

        assertTrue(changed.toString(), changed.stream().anyMatch(path -> path.endsWith("/.git")));
    }

    public void testAnAddedRemoteReachesFileListenersOnceTheConfigWasRead() throws Exception {
        git(project, "init", "-b", "main");
        VirtualFile directory = known(project);
        assertFalse(isClone(project));
        List<String> changed = recordFileEvents();

        git(project, "remote", "add", "origin", URL);
        VfsUtil.markDirtyAndRefresh(false, true, true, directory);

        assertTrue(changed.toString(), changed.stream().anyMatch(path -> path.endsWith("/.git/config")));
        assertTrue(isClone(project));
    }
}
