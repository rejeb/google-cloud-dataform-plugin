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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkflowSettingsGcpConfigProviderTest {

    @Test
    void theServiceAccountIsTheOneOfTheActiveRepository() {
        GcpRepositorySettings settings = mock(GcpRepositorySettings.class);
        when(settings.getActiveConfig()).thenReturn(new DataformRepositoryConfig(
                "id", "dev", "my-project", "my-repo", "europe-west1",
                "runner@my-project.iam.gserviceaccount.com"));

        assertEquals("runner@my-project.iam.gserviceaccount.com",
                new WorkflowSettingsGcpConfigProvider(settings).getServiceAccount());
    }

    @Test
    void thereIsNoServiceAccountWithoutAnActiveRepository() {
        GcpRepositorySettings settings = mock(GcpRepositorySettings.class);
        when(settings.getActiveConfig()).thenReturn(null);

        assertEquals("", new WorkflowSettingsGcpConfigProvider(settings).getServiceAccount());
    }
}
