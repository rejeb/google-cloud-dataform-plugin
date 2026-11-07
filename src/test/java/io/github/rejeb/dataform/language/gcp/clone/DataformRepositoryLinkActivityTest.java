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

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.gcp.settings.GcpRepositorySettings;

import java.nio.file.Path;
import java.util.List;

public class DataformRepositoryLinkActivityTest extends BasePlatformTestCase {

    private static final RepositoryLink LINK =
            new RepositoryLink("acme", "europe-west1", "analytics-core", "", null);

    private GcpRepositorySettings settings;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        settings = GcpRepositorySettings.getInstance(getProject());
        reset();
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            reset();
            PendingRepositoryLinks.getInstance().take(getProject().getBasePath());
        } finally {
            super.tearDown();
        }
    }

    private void reset() {
        settings.saveAllConfigs(List.of());
        settings.setActiveRepositoryId(null);
    }

    public void testTheProjectFetchedFromGoogleCloudIsLinkedWhenItOpens() {
        PendingRepositoryLinks.getInstance().register(Path.of(getProject().getBasePath()), LINK);

        DataformRepositoryLinkActivity.linkPendingRepository(getProject());

        assertEquals("analytics-core", settings.getActiveConfig().repositoryId());
    }

    public void testAnyOtherProjectIsLeftAlone() {
        PendingRepositoryLinks.getInstance().register(Path.of(getProject().getBasePath() + "-other"), LINK);

        DataformRepositoryLinkActivity.linkPendingRepository(getProject());

        assertEmpty(settings.getAllConfigs());
        assertEquals(LINK, PendingRepositoryLinks.getInstance().take(getProject().getBasePath() + "-other"));
    }
}
