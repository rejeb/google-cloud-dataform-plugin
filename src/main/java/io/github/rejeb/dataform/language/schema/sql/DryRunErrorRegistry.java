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

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;

/**
 * Keeps the message of the last failed BigQuery dry-run per compiled action, so views can report
 * why a schema could not be resolved. Entries live until the action is dry-run again or leaves the
 * compiled graph.
 */
public interface DryRunErrorRegistry {

    static DryRunErrorRegistry getInstance(@NotNull Project project) {
        return project.getService(DryRunErrorRegistry.class);
    }

    /** Records the message the dry-run of the given action failed with. */
    void report(@NotNull String actionFullName, @NotNull String message);

    /** Drops the recorded failure of an action whose dry-run succeeded. */
    void clear(@NotNull String actionFullName);

    /** Drops the failures of every action outside the given set. */
    void retainOnly(@NotNull Set<String> actionFullNames);

    /** The message the last dry-run of the action failed with, or {@code null} if it did not fail. */
    @Nullable
    String getError(@NotNull String actionFullName);

    /** An immutable snapshot of the recorded failures, keyed by action full name. */
    @NotNull
    Map<String, String> getErrors();
}
