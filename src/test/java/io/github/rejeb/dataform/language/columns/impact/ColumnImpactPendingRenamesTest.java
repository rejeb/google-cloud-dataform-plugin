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
package io.github.rejeb.dataform.language.columns.impact;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.columns.rename.downstream.DownstreamRename;
import io.github.rejeb.dataform.language.diagnostics.sql.bigquery.DryRunBuildProblem;
import io.github.rejeb.dataform.language.schema.sql.DryRunErrorRegistry;

import java.util.List;
import java.util.Set;

public class ColumnImpactPendingRenamesTest extends BasePlatformTestCase {

    private static final String CUSTOMERS = "proj.ds.customers";
    private static final String SILVER = "proj.ds.silver";
    private static final String FAILURE = "Unrecognized name: email at [3:3]";
    private static final DownstreamRename EMAIL =
            new DownstreamRename(CUSTOMERS, "email", "customer_email", Set.of(CUSTOMERS));

    private ColumnImpactServiceImpl service() {
        return (ColumnImpactServiceImpl) ColumnImpactService.getInstance(getProject());
    }

    private void publishRenameFailingSilver() {
        ColumnChange change = new ColumnChange(CUSTOMERS, ColumnChange.Kind.RENAMED,
                "email", "customer_email", "STRING", "STRING");
        service().publish(List.of(new ColumnImpact(change, List.of(new DryRunBuildProblem(SILVER, null, 0, 0,
                "Unrecognized name: email", FAILURE, "email")))), List.of(EMAIL));
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            DryRunErrorRegistry.getInstance(getProject()).clear(SILVER);
            service().publish(List.of(), List.of());
        } finally {
            super.tearDown();
        }
    }

    public void testARenameIsPendingWhileADownstreamActionStillFailsOnIt() {
        DryRunErrorRegistry.getInstance(getProject()).report(SILVER, FAILURE);
        publishRenameFailingSilver();

        assertEquals(List.of(EMAIL), service().pendingRenamesOf(CUSTOMERS));
        assertEquals(List.of(), service().pendingRenamesOf(SILVER));
    }

    public void testARenameIsNoLongerPendingOnceTheFailureIsGone() {
        DryRunErrorRegistry.getInstance(getProject()).report(SILVER, FAILURE);
        publishRenameFailingSilver();
        DryRunErrorRegistry.getInstance(getProject()).clear(SILVER);

        assertEquals(List.of(), service().pendingRenamesOf(CUSTOMERS));
    }
}
