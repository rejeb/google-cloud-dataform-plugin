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
package io.github.rejeb.dataform.language.schema.sql;

import io.github.rejeb.dataform.language.util.MappedText;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DryRunErrorRegistryImplTest {

    private final DryRunErrorRegistryImpl registry = new DryRunErrorRegistryImpl();

    @Test
    public void reportedErrorIsReadBackForItsAction() {
        registry.report("p.ds.users", "Syntax error at [3:5]");

        assertEquals("Syntax error at [3:5]", registry.getError("p.ds.users"));
    }

    @Test
    public void anActionWithoutFailureHasNoError() {
        assertNull(registry.getError("p.ds.users"));
    }

    @Test
    public void aLaterFailureReplacesThePreviousOne() {
        registry.report("p.ds.users", "first");
        registry.report("p.ds.users", "second");

        assertEquals("second", registry.getError("p.ds.users"));
    }

    @Test
    public void clearDropsTheErrorOfAnActionThatRanClean() {
        registry.report("p.ds.users", "boom");
        registry.clear("p.ds.users");

        assertNull(registry.getError("p.ds.users"));
    }

    @Test
    public void retainOnlyDropsActionsMissingFromTheCompiledGraph() {
        registry.report("p.ds.users", "boom");
        registry.report("p.ds.gone", "boom");

        registry.retainOnly(Set.of("p.ds.users"));

        assertEquals("boom", registry.getError("p.ds.users"));
        assertNull(registry.getError("p.ds.gone"));
    }

    @Test
    public void retainOnlyKeepsEverythingWhenTheGraphIsUnknown() {
        registry.report("p.ds.users", "boom");

        registry.retainOnly(Set.of());

        assertEquals("boom", registry.getError("p.ds.users"));
    }

    @Test
    public void theReturnedSnapshotIsImmutable() {
        registry.report("p.ds.users", "boom");

        assertThrows(UnsupportedOperationException.class,
                () -> registry.getErrors().put("p.ds.other", "boom"));
        assertTrue(registry.getErrors().containsKey("p.ds.users"));
    }

    @Test
    public void aFailureKeepsTheQueryThatWasSent() {
        MappedText sent = MappedText.identity(DryRunQueryText.MAIN_QUERY, "SELECT x");
        registry.reportFailure("p.ds.users", new DryRunFailure("Unrecognized name: x at [1:8]", sent));

        assertSame(sent, registry.getFailure("p.ds.users").query());
        assertEquals("Unrecognized name: x at [1:8]", registry.getError("p.ds.users"));
    }

    @Test
    public void aMessageAloneIsAFailureWithoutQuery() {
        registry.report("p.ds.users", "boom");

        assertNull(registry.getFailure("p.ds.users").query());
    }

    @Test
    public void onlyActualChangesAreCounted() {
        long start = registry.getModificationCount();
        registry.report("p.ds.users", "boom");
        long afterReport = registry.getModificationCount();
        registry.report("p.ds.users", "boom");
        long afterSameReport = registry.getModificationCount();
        registry.clear("p.ds.users");
        long afterClear = registry.getModificationCount();
        registry.clear("p.ds.users");

        assertTrue(afterReport > start);
        assertEquals(afterReport, afterSameReport);
        assertTrue(afterClear > afterSameReport);
        assertEquals(afterClear, registry.getModificationCount());
    }
}
