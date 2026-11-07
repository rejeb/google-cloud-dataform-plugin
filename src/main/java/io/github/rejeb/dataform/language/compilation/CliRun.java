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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * What the last run of the Dataform CLI compile command did, to explain a failure that produced
 * no compilation.
 *
 * @param command     the command line
 * @param directory   the working directory
 * @param nodePath    the directory of the Node.js interpreter, or {@code null} when none is configured
 * @param exitCode    the exit code, or {@code null} when the process did not start
 * @param stdoutBlank whether the standard output, which carries the compiled graph, was blank
 * @param stderr      the error output, or the reason the process did not start
 * @param durationMs  how long the run took
 */
public record CliRun(@NotNull String command, @NotNull String directory, @Nullable String nodePath,
                     @Nullable Integer exitCode, boolean stdoutBlank, @NotNull String stderr, long durationMs) {
}
