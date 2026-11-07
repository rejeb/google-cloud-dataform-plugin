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
package io.github.rejeb.dataform.language.compilation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CliFailureExplainerTest {

    private static final String COMMAND = "/usr/bin/node /opt/df/dataform compile --json";
    private static final String DIRECTORY = "/work/project";

    private static CliRun run(Integer exitCode, boolean stdoutBlank, String stderr) {
        return new CliRun(COMMAND, DIRECTORY, "/usr/bin", exitCode, stdoutBlank, stderr, 1200);
    }

    @Test
    void testAJsonOutputIsNotACliFailure() {
        assertNull(CliFailureExplainer.explain(run(0, false, "")));
    }

    @Test
    void testNotStarted() {
        CliRun run = run(null, true, "Cannot run program \"dataform\": error=2, No such file or directory");

        CliFailure failure = CliFailureExplainer.explain(run);

        assertNotNull(failure);
        assertEquals("Dataform CLI is not installed for the configured Node.js", failure.title());
        assertNull(failure.settingsKey());
        String description = CliFailureExplainer.describe(failure, run, false);
        assertTrue(description.contains("Exit code   not started"), description);
        assertTrue(description.contains("Cannot run program"), description);
    }

    @Test
    void testUnknownCoreVersion() {
        CliRun run = run(1, true, "npm error code ETARGET\n"
                + "npm error notarget No matching version found for @dataform/core@3.0.99.\n");

        CliFailure failure = CliFailureExplainer.explain(run);

        assertNotNull(failure);
        assertEquals("Dataform CLI failed: no version 3.0.99 of @dataform/core", failure.title());
        assertEquals("dataformCoreVersion", failure.settingsKey());
    }

    @Test
    void testGenericExit() {
        CliRun run = run(1, true, "Error: something broke\n    at main (cli.js:1:1)\n");

        CliFailure failure = CliFailureExplainer.explain(run);

        assertNotNull(failure);
        assertEquals("Dataform CLI failed (exit code 1)", failure.title());
        assertEquals("Error: something broke", failure.cause().getFirst());
        assertNull(failure.settingsKey());
    }

    @Test
    void testDescriptionListsCauseFixAndFacts() {
        CliRun run = run(1, true, "Error: something broke\n");
        String description = CliFailureExplainer.describe(CliFailureExplainer.explain(run), run, false);

        int cause = description.indexOf("Cause\n  Error: something broke");
        int fix = description.indexOf("\nFix\n  ");
        int command = description.indexOf("Command     " + COMMAND);
        assertTrue(cause >= 0 && fix > cause && command > fix, description);
        assertTrue(description.contains("Directory   " + DIRECTORY), description);
        assertTrue(description.contains("Node.js     /usr/bin"), description);
        assertTrue(description.contains("Exit code   1"), description);
        assertTrue(description.contains("Duration    "), description);
    }

    @Test
    void testPreviousGraphSentence() {
        CliRun run = run(1, true, "Error: something broke\n");
        CliFailure failure = CliFailureExplainer.explain(run);
        String sentence = "Nothing was compiled: previews, lineage and completion keep the last compilation";

        assertTrue(CliFailureExplainer.describe(failure, run, true).contains(sentence));
        assertFalse(CliFailureExplainer.describe(failure, run, false).contains(sentence));
    }

    @Test
    void testTheSettingsKeyIsFoundByTextSearch() {
        String yaml = "defaultProject: p\ndefaultLocation: EU\n  dataformCoreVersion: 3.0.99\n";

        assertEquals(yaml.indexOf("dataformCoreVersion"), CliFailureExplainer.keyOffset(yaml, "dataformCoreVersion"));
        assertEquals(-1, CliFailureExplainer.keyOffset("defaultProject: p\n# dataformCoreVersion: 1\n", "dataformCoreVersion"));
    }
}
