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

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a run of the Dataform CLI that compiled nothing into a failure the Build tool window
 * explains: the CLI did not start, it could not install the version of {@code @dataform/core} the
 * project asks for, or it exited with an error.
 */
public final class CliFailureExplainer {

    /**
     * The key of {@code workflow_settings.yaml} that sets the version of {@code @dataform/core}.
     */
    public static final String CORE_VERSION_KEY = "dataformCoreVersion";

    private static final Pattern UNKNOWN_CORE_VERSION =
            Pattern.compile("No matching version found for @dataform/core@([\\w.+-]*\\w)");
    private static final int MAX_CAUSE_LINES = 10;
    private static final int FACT_WIDTH = "Exit code   ".length();
    private static final String INDENT = "  ";
    private static final String PREVIOUS_GRAPH =
            "Nothing was compiled: previews, lineage and completion keep the last compilation.";

    private CliFailureExplainer() {
    }

    /**
     * Explains a run, or returns {@code null} when the run printed a compilation.
     */
    public static @Nullable CliFailure explain(@NotNull CliRun run) {
        if (run.exitCode() == null) {
            return new CliFailure("Dataform CLI is not installed for the configured Node.js",
                    concat(List.of("The Dataform CLI could not be started."), outputLines(run.stderr())),
                    List.of("Install the Dataform CLI in Settings › Tools › Dataform, or choose the Node.js "
                            + "interpreter it is installed for."),
                    null);
        }
        if (!run.stdoutBlank()) {
            return null;
        }
        Matcher version = UNKNOWN_CORE_VERSION.matcher(run.stderr());
        if (version.find()) {
            String wanted = version.group(1);
            return new CliFailure("Dataform CLI failed: no version " + wanted + " of @dataform/core",
                    List.of("npm has no version " + wanted + " of @dataform/core.",
                            "workflow_settings.yaml asks for it in " + CORE_VERSION_KEY + "."),
                    List.of("Set " + CORE_VERSION_KEY + " in workflow_settings.yaml to a published version of "
                            + "@dataform/core, then compile again."),
                    CORE_VERSION_KEY);
        }
        List<String> cause = outputLines(run.stderr());
        return new CliFailure("Dataform CLI failed (exit code " + run.exitCode() + ")",
                cause.isEmpty() ? List.of("The Dataform CLI printed no compilation and no error.") : cause,
                List.of("Run the command in a terminal, from the directory below, to see what the CLI reports."),
                null);
    }

    /**
     * The description of a failure for the details panel: what happened, its cause and fix, then
     * the facts of the run.
     *
     * @param previousGraph whether a compilation from before the run is still in use
     */
    public static @NotNull String describe(@NotNull CliFailure failure, @NotNull CliRun run, boolean previousGraph) {
        List<String> lines = new ArrayList<>();
        lines.add("The Dataform CLI stopped before compiling the project.");
        if (previousGraph) {
            lines.add(PREVIOUS_GRAPH);
        }
        lines.add("");
        lines.add("Cause");
        failure.cause().forEach(line -> lines.add(INDENT + line));
        lines.add("");
        lines.add("Fix");
        failure.fix().forEach(line -> lines.add(INDENT + line));
        lines.add("");
        lines.add(fact("Command", run.command()));
        lines.add(fact("Directory", run.directory()));
        lines.add(fact("Node.js", run.nodePath() != null ? run.nodePath() : "not configured"));
        lines.add(fact("Exit code", run.exitCode() == null ? "not started" : String.valueOf(run.exitCode())));
        lines.add(fact("Duration", StringUtil.formatDuration(run.durationMs())));
        lines.add("");
        lines.add("The complete output of the CLI is on the Dataform Compile node.");
        return BuildErrorText.details(String.join("\n", lines));
    }

    /**
     * The offset of a top-level or nested key of a YAML text, found by text search, or {@code -1}
     * when no line sets it.
     */
    public static int keyOffset(@NotNull String yaml, @NotNull String key) {
        Matcher matcher = Pattern.compile("(?m)^[ \\t]*(" + Pattern.quote(key) + ")[ \\t]*:").matcher(yaml);
        return matcher.find() ? matcher.start(1) : -1;
    }

    private static @NotNull List<String> outputLines(@NotNull String output) {
        List<String> lines = Arrays.stream(output.split("\r?\n"))
                .map(String::strip)
                .filter(line -> !line.isEmpty())
                .toList();
        if (lines.size() <= MAX_CAUSE_LINES) {
            return lines;
        }
        return concat(lines.subList(0, MAX_CAUSE_LINES), List.of("…"));
    }

    private static @NotNull List<String> concat(@NotNull List<String> first, @NotNull List<String> second) {
        List<String> all = new ArrayList<>(first);
        all.addAll(second);
        return all;
    }

    private static @NotNull String fact(@NotNull String key, @NotNull String value) {
        return key + " ".repeat(Math.max(1, FACT_WIDTH - key.length())) + value;
    }
}
