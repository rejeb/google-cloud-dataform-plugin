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
package io.github.rejeb.dataform.language.gcp.auth;

import com.intellij.ide.browsers.BrowserLauncher;
import com.intellij.ide.browsers.WebBrowser;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class DataformAuthEditorNotificationProviderTest extends BasePlatformTestCase {

    private static final String PRIVACY_POLICY_URL = "https://rejeb.is-a.dev/dataform-plugin/privacy-policy";

    private final List<String> openedUrls = new ArrayList<>();

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        ServiceContainerUtil.replaceService(ApplicationManager.getApplication(), BrowserLauncher.class,
                new RecordingBrowserLauncher(), getTestRootDisposable());
        DataformAuthState.getInstance().markAuthRequired(AuthTrigger.USER_ACTION);
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            DataformAuthState.getInstance().markAuthenticated();
        } catch (Throwable e) {
            addSuppressedException(e);
        } finally {
            super.tearDown();
        }
    }

    public void testSignInBannerLinksToThePrivacyPolicy() {
        List<HyperlinkLabel> links = UIUtil.findComponentsOfType(signInBanner(), HyperlinkLabel.class);
        HyperlinkLabel privacyLink = links.stream()
                .filter(link -> "Privacy Policy".equals(link.getText()))
                .findFirst()
                .orElse(null);
        assertNotNull("No Privacy Policy link among " + links.stream().map(HyperlinkLabel::getText).toList(),
                privacyLink);

        privacyLink.doClick();
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();

        assertEquals(List.of(PRIVACY_POLICY_URL), openedUrls);
    }

    private JComponent signInBanner() {
        myFixture.addFileToProject("workflow_settings.yaml", "defaultProject: my-project\n");
        myFixture.configureByText("a.sqlx", "select 1\n");
        FileEditor fileEditor = TextEditorProvider.getInstance().getTextEditor(myFixture.getEditor());
        Function<? super FileEditor, ? extends JComponent> banner = new DataformAuthEditorNotificationProvider()
                .collectNotificationData(getProject(), myFixture.getFile().getVirtualFile());
        assertNotNull("No sign-in banner is shown", banner);
        return banner.apply(fileEditor);
    }

    private final class RecordingBrowserLauncher extends BrowserLauncher {

        @Override
        public void open(@NotNull String url) {
            openedUrls.add(url);
        }

        @Override
        public void browse(@NotNull File file) {
            openedUrls.add(file.getPath());
        }

        @Override
        public void browse(@NotNull Path path) {
            openedUrls.add(path.toString());
        }

        @Override
        public void browse(@NotNull String url, @Nullable WebBrowser browser, @Nullable Project project) {
            openedUrls.add(url);
        }
    }
}
