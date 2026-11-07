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
package io.github.rejeb.dataform.language.gcp.toolwindow.action;

import com.google.auth.oauth2.GoogleCredentials;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.DataformIcons;
import io.github.rejeb.dataform.language.gcp.auth.DataformCredentialsService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GcpAuthActionTest extends BasePlatformTestCase {

    public void testSignedInItOffersToSignOutWithTheAccountInTheStatusBarOnly() {
        Presentation presentation = updatedPresentation(true, "jane@example.com");

        assertEquals("Sign Out of Google Cloud", presentation.getText());
        assertEquals("Signed in as jane@example.com", presentation.getDescription());
        assertSame(DataformIcons.ACCOUNT, presentation.getIcon());
    }

    public void testSignedOutItOffersToSignIn() {
        Presentation presentation = updatedPresentation(false, null);

        assertEquals("Sign In to Google Cloud", presentation.getText());
        assertSame(DataformIcons.ACCOUNT, presentation.getIcon());
    }

    private Presentation updatedPresentation(boolean signedIn, @Nullable String email) {
        ServiceContainerUtil.replaceService(ApplicationManager.getApplication(), DataformCredentialsService.class,
                new FakeCredentials(signedIn, email), getTestRootDisposable());
        GcpAuthAction action = new GcpAuthAction(getProject());
        AnActionEvent event = TestActionEvent.createTestEvent(action);
        action.update(event);
        return event.getPresentation();
    }

    private record FakeCredentials(boolean signedIn, @Nullable String email) implements DataformCredentialsService {

        @Override
        public @NotNull GoogleCredentials get() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void invalidate() {
        }

        @Override
        public boolean isSignedIn() {
            return signedIn;
        }

        @Override
        public boolean isSignedInCached() {
            return signedIn;
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
    }
}
