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

import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.gcp.service.DataformGcpEvent;
import io.github.rejeb.dataform.language.gcp.settings.DataformRepositoryConfig;
import io.github.rejeb.dataform.language.gcp.settings.GcpRepositorySettings;
import io.github.rejeb.dataform.language.setup.DataformPackageInstaller;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class RepositoryLinkerTest extends BasePlatformTestCase {

    private static final RepositoryLink LINK = new RepositoryLink("acme", "europe-west1", "analytics-core",
            "dataform@acme.iam.gserviceaccount.com", null);

    private final List<VirtualFile> installs = new ArrayList<>();
    private GcpRepositorySettings settings;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        settings = GcpRepositorySettings.getInstance(getProject());
        reset();
        ServiceContainerUtil.replaceService(getProject(), DataformPackageInstaller.class,
                installs::add, getTestRootDisposable());
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            reset();
        } finally {
            super.tearDown();
        }
    }

    private void reset() {
        settings.saveAllConfigs(List.of());
        settings.setActiveRepositoryId(null);
    }

    public void testANewRepositoryIsAddedAndBecomesActive() {
        RepositoryLinker.link(getProject(), LINK);

        DataformRepositoryConfig active = settings.getActiveConfig();
        assertNotNull(active);
        assertEquals("acme", active.projectId());
        assertEquals("europe-west1", active.location());
        assertEquals("analytics-core", active.repositoryId());
        assertEquals("dataform@acme.iam.gserviceaccount.com", active.serviceAccount());
        assertEquals("analytics-core", active.displayName());
        assertEquals(active.repositoryConfigId(), settings.getActiveRepositoryId());
    }

    public void testOtherRepositoriesAreKept() {
        DataformRepositoryConfig other = new DataformRepositoryConfig("other", "Other", "acme", "other", "us-central1");
        settings.saveAllConfigs(List.of(other));

        RepositoryLinker.link(getProject(), LINK);

        assertEquals(2, settings.getAllConfigs().size());
        assertEquals(other, settings.getAllConfigs().getFirst());
        assertEquals("analytics-core", settings.getActiveConfig().repositoryId());
    }

    public void testARepositoryAlreadyConfiguredIsActivatedInsteadOfAddedAgain() {
        DataformRepositoryConfig first = new DataformRepositoryConfig("first", "First", "acme", "first", "us-central1");
        DataformRepositoryConfig existing = new DataformRepositoryConfig("existing", "Sales",
                "acme", "analytics-core", "europe-west1", "kept@acme.iam.gserviceaccount.com");
        settings.saveAllConfigs(List.of(first, existing));

        RepositoryLinker.link(getProject(), LINK);

        assertEquals(List.of(first, existing), settings.getAllConfigs());
        assertEquals("existing", settings.getActiveRepositoryId());
    }

    public void testTheSameNameInAnotherLocationIsAnotherRepository() {
        settings.saveAllConfigs(List.of(new DataformRepositoryConfig("existing", "Sales",
                "acme", "analytics-core", "us-central1")));

        RepositoryLinker.link(getProject(), LINK);

        assertEquals(2, settings.getAllConfigs().size());
        assertEquals("europe-west1", settings.getActiveConfig().location());
    }

    public void testTheDownloadedWorkspaceIsSelected() {
        RepositoryLinker.link(getProject(), new RepositoryLink("acme", "europe-west1", "finance-marts", "", "dev-you"));

        assertEquals("dev-you", settings.getSelectedWorkspaceId());
    }

    public void testOpenViewsHearThatTheRepositoryChanged() {
        AtomicInteger changes = new AtomicInteger();
        getProject().getMessageBus().connect(getTestRootDisposable()).subscribe(DataformGcpEvent.TOPIC, new DataformGcpEvent() {
            @Override
            public void onRepositoryChanged() {
                changes.incrementAndGet();
            }
        });

        RepositoryLinker.link(getProject(), LINK);
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();

        assertEquals(1, changes.get());
    }

    public void testPackagesAreInstalledWhenThePackageFileHasNoInstallYet() {
        myFixture.addFileToProject("package.json", "{}");

        RepositoryLinker.link(getProject(), LINK);

        assertEquals(List.of(ProjectUtil.guessProjectDir(getProject())), installs);
    }

    public void testNothingIsInstalledWithoutAPackageFile() {
        RepositoryLinker.link(getProject(), LINK);

        assertEmpty(installs);
    }

    public void testNothingIsInstalledWhenThePackagesAreAlreadyThere() {
        myFixture.addFileToProject("package.json", "{}");
        myFixture.addFileToProject("node_modules/@dataform/core/package.json", "{}");

        RepositoryLinker.link(getProject(), LINK);

        assertEmpty(installs);
    }
}
