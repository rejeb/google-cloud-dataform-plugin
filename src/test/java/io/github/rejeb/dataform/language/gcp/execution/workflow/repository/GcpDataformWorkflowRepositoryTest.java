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
package io.github.rejeb.dataform.language.gcp.execution.workflow.repository;

import com.google.cloud.dataform.v1.InvocationConfig;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.InvocationTarget;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.WorkflowRunRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GcpDataformWorkflowRepositoryTest {

    private static final WorkflowRunRequest REQUEST = new WorkflowRunRequest(
            "intellij-run-1",
            List.of("daily"),
            List.of(new InvocationTarget("my-project", "gold", "orders")),
            true,
            true,
            true);

    @Test
    void theInvocationRunsAsTheServiceAccount() {
        InvocationConfig config = GcpDataformWorkflowRepository.invocationConfig(
                REQUEST, "runner@my-project.iam.gserviceaccount.com");

        assertEquals("runner@my-project.iam.gserviceaccount.com", config.getServiceAccount());
    }

    @Test
    void surroundingSpacesAreNotSentAsPartOfTheServiceAccount() {
        InvocationConfig config = GcpDataformWorkflowRepository.invocationConfig(
                REQUEST, "  runner@my-project.iam.gserviceaccount.com ");

        assertEquals("runner@my-project.iam.gserviceaccount.com", config.getServiceAccount());
    }

    @Test
    void aBlankServiceAccountLeavesTheIdentityToTheRepository() {
        InvocationConfig config = GcpDataformWorkflowRepository.invocationConfig(REQUEST, "   ");

        assertEquals("", config.getServiceAccount());
    }

    @Test
    void theInvocationKeepsTheSelectionOfTheRequest() {
        InvocationConfig config = GcpDataformWorkflowRepository.invocationConfig(REQUEST, "");

        assertEquals(List.of("daily"), config.getIncludedTagsList());
        assertEquals("my-project", config.getIncludedTargets(0).getDatabase());
        assertEquals("gold", config.getIncludedTargets(0).getSchema());
        assertEquals("orders", config.getIncludedTargets(0).getName());
        assertTrue(config.getTransitiveDependenciesIncluded());
        assertTrue(config.getTransitiveDependentsIncluded());
        assertTrue(config.getFullyRefreshIncrementalTablesEnabled());
    }
}
