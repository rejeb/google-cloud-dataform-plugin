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
package io.github.rejeb.dataform.language.lineage.dataplex;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * The lineage view asks for the scope on a pooled thread, outside any read action, while the
 * project defaults are read from {@code workflow_settings.yaml}.
 */
public class DataplexLineageScopeThreadingTest extends BasePlatformTestCase {

    public void testTheScopeIsReadOnAPooledThreadOutsideAReadAction() throws Exception {
        myFixture.addFileToProject("workflow_settings.yaml", "defaultProject: from-settings\ndefaultLocation: EU\n");
        myFixture.addFileToProject("definitions/a.sqlx", "SELECT 1");
        DataplexLineageServiceImpl service = new DataplexLineageServiceImpl(getProject());

        Future<LineageScope> scope = ApplicationManager.getApplication()
                .executeOnPooledThread(() -> service.scopeOf(new CompiledGraph()));

        assertEquals("projects/from-settings/locations/eu", scope.get(30, TimeUnit.SECONDS).parent());
    }
}
