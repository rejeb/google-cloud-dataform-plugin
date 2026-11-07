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

/**
 * Words what a build compiled, for the console of the build node.
 */
final class BuildSummaryText {

    private BuildSummaryText() {
    }

    /**
     * The summary lines, each ending with a line break.
     *
     * @param schemaCheck the result of the BigQuery check as {@link SchemaCheckText} words it, or
     *                    {@code null} when no check followed the compilation
     */
    static @NotNull String of(int actions, int errors, int files, @Nullable String schemaCheck) {
        StringBuilder text = new StringBuilder()
                .append("Compiled ").append(actions).append(" action(s), ")
                .append(errors).append(" compilation error(s) in ")
                .append(files).append(" file(s).\n");
        if (schemaCheck != null && !schemaCheck.isBlank()) {
            text.append(StringUtil.capitalize(schemaCheck.strip())).append(".\n");
        }
        return text.toString();
    }

    /**
     * The command and the directory of a run, then a blank line; empty without a run.
     */
    static @NotNull String header(@Nullable CliRun run) {
        return run == null ? "" : "$ " + run.command() + "\n  in " + run.directory() + "\n\n";
    }

    /**
     * How the process of a run ended.
     */
    static @NotNull String processEnd(@NotNull CliRun run) {
        return run.exitCode() == null ? "Process not started\n" : "Process finished with exit code " + run.exitCode() + "\n";
    }
}
