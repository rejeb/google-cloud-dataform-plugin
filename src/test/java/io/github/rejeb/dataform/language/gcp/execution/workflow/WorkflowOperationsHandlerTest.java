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
package io.github.rejeb.dataform.language.gcp.execution.workflow;

import io.github.rejeb.dataform.language.gcp.common.GcpConfigProvider;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.WorkflowCreationResult;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.WorkflowRunRequest;
import io.github.rejeb.dataform.language.gcp.execution.workflow.repository.WorkflowRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowOperationsHandlerTest {

    @Test
    void aRunIsCreatedWithTheServiceAccountOfTheActiveRepository() {
        WorkflowRepository repository = mock(WorkflowRepository.class);
        GcpConfigProvider config = mock(GcpConfigProvider.class);
        when(config.getProjectId()).thenReturn("my-project");
        when(config.getLocation()).thenReturn("europe-west1");
        when(config.getRepositoryId()).thenReturn("my-repo");
        when(config.getServiceAccount()).thenReturn("runner@my-project.iam.gserviceaccount.com");
        when(repository.createWorkflowRun(any(), any(), any(), any(), any()))
                .thenReturn(new WorkflowCreationResult("invocation", "workspace"));
        WorkflowRunRequest request = new WorkflowRunRequest(
                "intellij-run-1", List.of(), List.of(), false, false, false);

        new WorkflowOperationsHandler(repository, config).createWorkflowRun(request);

        verify(repository).createWorkflowRun(
                "my-project",
                "europe-west1",
                "my-repo",
                "runner@my-project.iam.gserviceaccount.com",
                request);
    }
}
