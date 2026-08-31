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
package io.github.rejeb.dataform.language.refactoring.column.ui;

import com.intellij.openapi.project.Project;
import io.github.rejeb.dataform.language.refactoring.column.plan.ColumnRenamePlan;
import io.github.rejeb.dataform.language.refactoring.column.plan.StarResolution;
import org.jetbrains.annotations.NotNull;

/**
 * Asks what to do about the columns of a rename that a star produces.
 *
 * <p>A service rather than a call to a dialog, so that a test can answer without a modal.</p>
 */
public interface StarResolutionChooser {

    static StarResolutionChooser getInstance(@NotNull Project project) {
        return project.getService(StarResolutionChooser.class);
    }

    /**
     * The user's answer for the star boundaries of {@code plan}.
     */
    @NotNull
    StarResolution choose(@NotNull Project project, @NotNull ColumnRenamePlan plan);
}
