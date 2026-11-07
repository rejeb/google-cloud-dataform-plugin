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
package io.github.rejeb.dataform.language.errorreport;

import com.intellij.diagnostic.DefaultIdeaErrorLogger;
import com.intellij.diagnostic.JetBrainsMarketplaceErrorReportSubmitter;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.diagnostic.ErrorReportSubmitter;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.lang.reflect.Constructor;
import java.util.List;

/**
 * The platform sends a third-party plugin's reports automatically only when its submitter is exactly
 * {@link JetBrainsMarketplaceErrorReportSubmitter}, so a subclass or a wrapper would not do.
 */
public class MarketplaceErrorReportingTest extends BasePlatformTestCase {

    private static final String PLUGIN_ID = "io.github.rejeb.gcp-dataform-plugin";

    private static ErrorReportSubmitter submitterOf(Throwable throwable) {
        IdeaPluginDescriptor plugin = PluginManagerCore.getPlugin(PluginId.getId(PLUGIN_ID));
        assertNotNull("the plugin is not loaded", plugin);
        ErrorReportSubmitter submitter = DefaultIdeaErrorLogger.findSubmitter(throwable, plugin);
        assertNotNull("the plugin registers no error handler", submitter);
        return submitter;
    }

    public void testExceptionsOfThePluginGoToTheMarketplaceSubmitter() {
        assertSame(JetBrainsMarketplaceErrorReportSubmitter.class, submitterOf(new RuntimeException("boom")).getClass());
    }

    public void testFreezesOfThePluginGoToTheMarketplaceSubmitter() throws ReflectiveOperationException {
        Constructor<?> freeze = Class.forName("com.intellij.diagnostic.Freeze").getDeclaredConstructor(List.class);
        freeze.setAccessible(true);
        Throwable throwable = (Throwable) freeze.newInstance(List.of());

        assertSame(JetBrainsMarketplaceErrorReportSubmitter.class, submitterOf(throwable).getClass());
    }
}
