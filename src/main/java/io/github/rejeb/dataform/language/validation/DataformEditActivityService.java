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
package io.github.rejeb.dataform.language.validation;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Tells whether the user is currently editing a Dataform source. Half-typed text produces
 * problems that are neither real nor actionable, so the compilation and validation feedback is
 * held back until the user pauses; a change reaching disk is reported through the virtual file
 * system instead and needs no pause.
 */
public interface DataformEditActivityService {

    /** How long after the last edit the user is still considered to be typing. */
    long QUIET_PERIOD_MS = 5_000;

    static DataformEditActivityService getInstance(@NotNull Project project) {
        return project.getService(DataformEditActivityService.class);
    }

    /** Records that a Dataform source was just edited. */
    void noteEdit();

    /** Whether the last edit is recent enough that the user is considered still typing. */
    boolean isEditing();

    /** Milliseconds left before the user counts as having stopped typing, {@code 0} once idle. */
    long remainingQuietPeriodMs();
}
