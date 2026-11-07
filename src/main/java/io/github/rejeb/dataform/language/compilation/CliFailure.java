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

import java.util.List;

/**
 * Why the Dataform CLI compiled nothing, and how to fix it.
 *
 * @param title       the one-line summary, used as the label of the Build tree node
 * @param cause       the lines explaining the cause
 * @param fix         the lines explaining the fix
 * @param settingsKey the key of {@code workflow_settings.yaml} the failure is about, or {@code null}
 */
public record CliFailure(@NotNull String title, @NotNull List<String> cause, @NotNull List<String> fix,
                         @Nullable String settingsKey) {
}
