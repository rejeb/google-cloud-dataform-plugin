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

import com.google.auth.oauth2.GoogleCredentials;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.ui.cloneDialog.VcsCloneDialogExtension;
import com.intellij.openapi.vcs.ui.cloneDialog.VcsCloneDialogExtensionComponent;
import com.intellij.openapi.vcs.ui.cloneDialog.VcsCloneDialogExtensionStatusLine;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.gcp.auth.DataformCredentialsService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class DataformCloneDialogExtensionTest extends BasePlatformTestCase {

    private String email;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        ServiceContainerUtil.replaceService(ApplicationManager.getApplication(), DataformCredentialsService.class,
                new DataformCredentialsService() {
                    @Override
                    public @NotNull GoogleCredentials get() {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public void invalidate() {
                    }

                    @Override
                    public boolean isSignedIn() {
                        return email != null;
                    }

                    @Override
                    public boolean isSignedInCached() {
                        return email != null;
                    }

                    @Override
                    public @Nullable String getAccountEmail() {
                        return email;
                    }

                    @Override
                    public void signIn(@Nullable Project project) {
                    }

                    @Override
                    public void signOut() {
                    }
                }, getTestRootDisposable());
    }

    private static VcsCloneDialogExtension registered() {
        VcsCloneDialogExtension extension =
                VcsCloneDialogExtension.Companion.getEP_NAME().findExtension(DataformCloneDialogExtension.class);
        assertNotNull("the Dataform tab is not registered in the clone dialog", extension);
        return extension;
    }

    public void testTheDialogOffersDataformThroughTheGitOptionalDescriptor() {
        assertEquals("Dataform", registered().getName());
        assertTrue(DataformCloneDialogs.isAvailable());
    }

    public void testTheAccountIsShownUnderTheTabName() {
        email = "you@example.com";

        List<VcsCloneDialogExtensionStatusLine> lines = registered().getAdditionalStatusLines();

        assertEquals(List.of("you@example.com"), lines.stream().map(VcsCloneDialogExtensionStatusLine::getText).toList());
    }

    public void testNoAccountShowsNoLine() {
        assertEmpty(registered().getAdditionalStatusLines());
    }

    public void testTheDialogBelongsToTheProjectItWasAskedFrom() {
        assertSame(getProject(), DataformCloneDialogs.dialogProject(getProject()));
    }

    public void testWithoutAProjectTheDialogBelongsToAnOpenOne() {
        assertFalse(DataformCloneDialogs.dialogProject(null).isDefault());
    }

    public void testTheTabIsBuiltForAProject() {
        VcsCloneDialogExtensionComponent component =
                registered().createMainComponent(getProject(), ModalityState.nonModal());
        Disposer.register(getTestRootDisposable(), component);

        assertNotNull(component.getView());
    }
}
