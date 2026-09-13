/*
 * Copyright 2025 Rejeb Ben Rejeb
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.rejeb.dataform.language.testing;

import com.intellij.openapi.application.AccessToken;
import com.intellij.testFramework.LoggedErrorProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;

import java.util.EnumSet;
import java.util.Set;

/**
 * Keeps errors the platform logs about itself from failing every test in the JVM.
 *
 * <p>Both are raised by the platform while a test opens its project, through the logger the test
 * framework turns into a failure of that test, and neither can be influenced from this plugin:
 * the 2026.2 test framework registers the Java plugin's internal actions while the module
 * declaring their parent group is not loaded, and the usage statistics parse the marketplace
 * cache with the platform's Jackson while the test classpath, unlike a plugin classloader, lets
 * the older Jackson annotations of the Google Cloud libraries shadow the platform's. The
 * messages are still written to the test log.</p>
 */
public class KnownPlatformErrorsSessionListener implements LauncherSessionListener {

    private static final String[] KNOWN = {
            "group with id \"Internal.Java\" isn't registered",
            "does not have member field 'com.fasterxml.jackson.annotation.JsonFormat$Shape POJO'"
    };

    private AccessToken token;

    @Override
    public void launcherSessionOpened(LauncherSession session) {
        token = LoggedErrorProcessor.executeWith(new LoggedErrorProcessor() {
            @Override
            public @NotNull Set<Action> processError(@NotNull String category,
                                                     @NotNull String message,
                                                     String @NotNull [] details,
                                                     @Nullable Throwable throwable) {
                for (String known : KNOWN) {
                    if (message.contains(known)) return EnumSet.of(Action.LOG);
                }
                return Action.ALL;
            }
        });
    }

    @Override
    public void launcherSessionClosed(LauncherSession session) {
        if (token != null) token.finish();
    }
}
