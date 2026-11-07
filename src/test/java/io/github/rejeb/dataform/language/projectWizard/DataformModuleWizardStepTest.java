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
package io.github.rejeb.dataform.language.projectWizard;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ui.cloneDialog.VcsCloneDialogExtension;
import com.intellij.testFramework.ExtensionTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.components.ActionLink;
import io.github.rejeb.dataform.language.setup.DataformCoreVersionProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DataformModuleWizardStepTest extends BasePlatformTestCase {

    private final List<Project> dialogsOpened = new ArrayList<>();

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        DataformCoreVersionProvider versions = mock(DataformCoreVersionProvider.class);
        when(versions.knownLatestVersion()).thenReturn("3.0.56");
        when(versions.installedCliVersion()).thenReturn(Optional.empty());
        when(versions.fetchLatestVersion()).thenReturn(Optional.empty());
        ServiceContainerUtil.replaceService(ApplicationManager.getApplication(), DataformCoreVersionProvider.class,
                versions, getTestRootDisposable());
    }

    private DataformModuleWizardStep step() {
        return new DataformModuleWizardStep(new DataformModuleBuilder(), getProject(), dialogsOpened::add);
    }

    public void testTheWizardLeadsToTheDialogTabWhenItExists() {
        ActionLink link = step().existingRepositoryLink();

        assertNotNull(link);
        assertEquals("Get it from Google Cloud", link.getText());
    }

    public void testWithoutTheDialogTabThereIsNoLink() {
        ExtensionTestUtil.maskExtensions(VcsCloneDialogExtension.Companion.getEP_NAME(), List.of(), getTestRootDisposable());

        assertNull(step().existingRepositoryLink());
    }

    public void testFollowingTheLinkOpensTheDialogForTheProject() {
        ActionLink link = step().existingRepositoryLink();

        link.doClick();
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();

        assertEquals(List.of(getProject()), dialogsOpened);
    }
}
