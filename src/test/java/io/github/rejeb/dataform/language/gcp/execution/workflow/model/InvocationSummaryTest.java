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
package io.github.rejeb.dataform.language.gcp.execution.workflow.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class InvocationSummaryTest {

    private static final String INVOCATION =
            "projects/p/locations/eu/repositories/r/workflowInvocations/inv-1";
    private static final String WORKSPACE =
            "projects/p/locations/eu/repositories/r/workspaces/dev";

    @Test
    public void compilationResultIdIsTheLastSegment() {
        assertEquals("cr-9", summary(INVOCATION, "projects/p/locations/eu/repositories/r/compilationResults/cr-9", null)
                .compilationResultId());
        assertEquals("plain", summary(INVOCATION, "plain", null).compilationResultId());
    }

    @Test
    public void consoleUrlPointsAtTheInvocation() {
        assertEquals("https://console.cloud.google.com/bigquery/dataform/locations/eu/repositories/r"
                        + "/workflows/inv-1?project=p",
                summary(INVOCATION, "cr", null).gcpConsoleUrl());
    }

    @Test
    public void consoleUrlFallsBackToTheConsoleRootForAMalformedName() {
        assertEquals("https://console.cloud.google.com/", summary("bad/name", "cr", null).gcpConsoleUrl());
    }

    @Test
    public void workspaceUrlPointsAtTheSourceWorkspace() {
        assertEquals("https://console.cloud.google.com/bigquery/dataform/locations/eu/repositories/r"
                        + "/workspaces/dev?project=p",
                summary(INVOCATION, "cr", WORKSPACE).workspaceConsoleUrl());
    }

    @Test
    public void workspaceUrlIsAbsentWithoutAWorkspaceSource() {
        assertNull(summary(INVOCATION, "cr", null).workspaceConsoleUrl());
        assertNull(summary(INVOCATION, "cr", "short/name").workspaceConsoleUrl());
    }

    private static InvocationSummary summary(String invocation, String compilationResult, String workspace) {
        return new InvocationSummary(invocation, compilationResult, "WORKSPACE", workspace, null,
                Instant.EPOCH, null);
    }
}
