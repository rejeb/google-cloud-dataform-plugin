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
package io.github.rejeb.dataform.language.gcp.settings;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.List;

public class GcpRepositorySettingsImplTest extends BasePlatformTestCase {

    private static final String ID = "cfg-1";

    private GcpRepositorySettingsImpl settings() {
        GcpRepositorySettingsImpl settings = new GcpRepositorySettingsImpl(getProject());
        settings.saveAllConfigs(List.of(new DataformRepositoryConfig(
                ID, "Prod", "project-a", "repo-a", "europe-west1", "sa@a.iam")));
        settings.setActiveRepositoryId(ID);
        settings.setSelectedWorkspaceId("dev");
        return settings;
    }

    public void testEditingAnExistingConfigPersistsEveryField() {
        GcpRepositorySettingsImpl settings = settings();

        settings.saveAllConfigs(List.of(new DataformRepositoryConfig(
                ID, "Staging", "project-b", "repo-b", "us-central1", "sa@b.iam")));

        DataformRepositoryConfig saved = settings.getAllConfigs().getFirst();
        assertEquals("Staging", saved.label());
        assertEquals("project-b", saved.projectId());
        assertEquals("repo-b", saved.repositoryId());
        assertEquals("us-central1", saved.location());
        assertEquals("sa@b.iam", saved.serviceAccount());
    }

    public void testEditingAnExistingConfigKeepsItsSelectedWorkspace() {
        GcpRepositorySettingsImpl settings = settings();

        settings.saveAllConfigs(List.of(new DataformRepositoryConfig(
                ID, "Renamed", "project-a", "repo-a", "europe-west1", "sa@a.iam")));

        assertEquals(ID, settings.getActiveRepositoryId());
        assertEquals("dev", settings.getSelectedWorkspaceId());
    }
}
