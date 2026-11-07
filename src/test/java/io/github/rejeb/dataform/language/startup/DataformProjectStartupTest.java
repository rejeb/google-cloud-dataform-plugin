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
package io.github.rejeb.dataform.language.startup;

import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.gcp.service.DataformGcpService;
import io.github.rejeb.dataform.language.gcp.settings.DataformRepositoryConfig;
import io.github.rejeb.dataform.language.gcp.settings.GcpRepositorySettings;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DataformProjectStartupTest extends BasePlatformTestCase {

    private static final DataformRepositoryConfig SALES =
            new DataformRepositoryConfig("sales-id", "Sales", "p", "sales", "europe-west1");

    private DataformGcpService service;
    private GcpRepositorySettings settings;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        settings = GcpRepositorySettings.getInstance(getProject());
        settings.saveAllConfigs(List.of(SALES));
        settings.setActiveRepositoryId(SALES.repositoryConfigId());
        settings.setSelectedWorkspaceId(null);
        service = mock(DataformGcpService.class);
        when(service.getCachedFiles()).thenReturn(List.of());
        ServiceContainerUtil.replaceService(getProject(), DataformGcpService.class, service, getTestRootDisposable());
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            settings.saveAllConfigs(List.of());
            settings.setActiveRepositoryId(null);
            settings.setSelectedWorkspaceId(null);
        } finally {
            super.tearDown();
        }
    }

    public void testTheCacheIsLoadedFromTheSelectedWorkspace() {
        settings.setSelectedWorkspaceId("dev");

        DataformProjectStartup.loadRemoteFileCache(getProject());

        verify(service).refreshFilesAsync(eq("dev"), any());
    }

    public void testWithoutAWorkspaceTheServiceDecidesWhatCanBeRead() {
        DataformProjectStartup.loadRemoteFileCache(getProject());

        verify(service).refreshFilesAsync(isNull(), any());
    }

    public void testACachedListCallsNothing() {
        when(service.getCachedFiles()).thenReturn(List.of("definitions/a.sqlx"));

        DataformProjectStartup.loadRemoteFileCache(getProject());

        verify(service, never()).refreshFilesAsync(any(), any());
    }

    public void testNoRepositoryCallsNothing() {
        settings.setActiveRepositoryId(null);
        settings.saveAllConfigs(List.of());

        DataformProjectStartup.loadRemoteFileCache(getProject());

        verify(service, never()).refreshFilesAsync(any(), any());
    }
}
