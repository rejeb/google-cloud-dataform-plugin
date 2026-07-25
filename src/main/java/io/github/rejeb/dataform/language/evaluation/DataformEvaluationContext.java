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
package io.github.rejeb.dataform.language.evaluation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Everything the Node harness needs to reproduce the Dataform evaluation environment.
 *
 * @param projectConfig  the {@code dataform.projectConfig} object, in {@code @dataform/core} naming
 * @param refTargets     action name to fully qualified target, backing {@code ref()} and {@code resolve()}
 * @param selfTarget     fully qualified target of the file being evaluated, backing {@code self()}
 * @param includeSources global name to source text for every {@code includes/*.js} file
 * @param fileScript     JavaScript declared by the evaluated file itself, from its {@code js} blocks
 * @param nodePaths      module resolution roots appended to {@code module.paths}
 */
public record DataformEvaluationContext(@NotNull Map<String, Object> projectConfig,
                                        @NotNull Map<String, String> refTargets,
                                        @Nullable String selfTarget,
                                        @NotNull Map<String, String> includeSources,
                                        @Nullable String fileScript,
                                        @NotNull List<String> nodePaths) {
}
