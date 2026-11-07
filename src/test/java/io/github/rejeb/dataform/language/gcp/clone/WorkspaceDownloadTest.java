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

import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.gcp.common.GcpApiException;
import io.github.rejeb.dataform.language.gcp.workspace.DataformRepository;
import io.github.rejeb.dataform.language.gcp.workspace.repository.WorkspaceRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class WorkspaceDownloadTest extends BasePlatformTestCase {

    private static final DataformRepository REPOSITORY = new DataformRepository(
            "finance-marts", null, null, "dataform@acme.iam.gserviceaccount.com");

    private final List<Path> opened = new ArrayList<>();
    private final PendingRepositoryLinks links = new PendingRepositoryLinksImpl();
    private WorkspaceRepository repository;
    private Path root;
    private Path directory;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        repository = mock(WorkspaceRepository.class);
        root = Files.createTempDirectory("dataform-download");
        directory = root.resolve("parent").resolve("finance-marts");
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            NioFiles.deleteRecursively(root);
        } finally {
            super.tearDown();
        }
    }

    private RepositoryCheckoutRequest request() {
        return new RepositoryCheckoutRequest("acme", "europe-west1", REPOSITORY,
                RepositorySource.WORKSPACE, "dev-you", directory);
    }

    private void workspaceHolds(Map<String, String> files) {
        when(repository.readAllFiles("acme", "europe-west1", "finance-marts", "dev-you")).thenReturn(files);
    }

    private void download(ProgressIndicator indicator) throws IOException {
        new WorkspaceDownload(repository, links, opened::add).run(request(), indicator);
    }

    public void testTheFilesOfTheChosenWorkspaceAreWrittenAndTheProjectOpens() throws IOException {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("workflow_settings.yaml", "defaultProject: acme\n");
        files.put("definitions/gold/orders.sqlx", "select é\n");
        workspaceHolds(files);

        download(new EmptyProgressIndicator());

        assertEquals("defaultProject: acme\n", Files.readString(directory.resolve("workflow_settings.yaml")));
        assertEquals("select é\n", Files.readString(directory.resolve("definitions/gold/orders.sqlx")));
        assertEquals(List.of(directory), opened);
    }

    public void testTheLinkIsWaitingWhenTheProjectOpens() throws IOException {
        workspaceHolds(Map.of("workflow_settings.yaml", ""));
        List<RepositoryLink> seenAtOpening = new ArrayList<>();

        new WorkspaceDownload(repository, links, dir -> seenAtOpening.add(links.take(dir.toString())))
                .run(request(), new EmptyProgressIndicator());

        assertEquals(List.of(new RepositoryLink("acme", "europe-west1", "finance-marts",
                "dataform@acme.iam.gserviceaccount.com", "dev-you")), seenAtOpening);
    }

    public void testAnEmptyWorkspaceStillOpensAProject() throws IOException {
        workspaceHolds(Map.of());

        download(new EmptyProgressIndicator());

        assertTrue(Files.isDirectory(directory));
        assertEquals(List.of(directory), opened);
    }

    public void testAPathLeavingTheDirectoryIsRefused() {
        workspaceHolds(Map.of("../../evil.txt", "boom"));

        assertThrows(IOException.class, () -> download(new EmptyProgressIndicator()));

        assertFalse(Files.exists(root.resolve("evil.txt")));
        assertEmpty(opened);
        assertNull(links.take(directory.toString()));
    }

    public void testAnApiFailureOpensNothing() {
        when(repository.readAllFiles("acme", "europe-west1", "finance-marts", "dev-you"))
                .thenThrow(new GcpApiException("denied", new RuntimeException()));

        assertThrows(GcpApiException.class, () -> download(new EmptyProgressIndicator()));

        assertEmpty(opened);
        assertNull(links.take(directory.toString()));
    }

    public void testACancelledDownloadOpensNothing() {
        workspaceHolds(Map.of("workflow_settings.yaml", ""));
        ProgressIndicator indicator = new EmptyProgressIndicator();
        indicator.cancel();

        assertThrows(ProcessCanceledException.class, () -> download(indicator));

        assertEmpty(opened);
    }
}
