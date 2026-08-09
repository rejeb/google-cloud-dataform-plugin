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

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Recompiles the Dataform project in the background after source files are saved or edited.
 */
public interface DataformAutoCompileService {

    /**
     * Returns the project-level instance.
     */
    static DataformAutoCompileService getInstance(@NotNull Project project) {
        return project.getService(DataformAutoCompileService.class);
    }

    /**
     * Requests a recompilation of a saved file, run as soon as the editor is idle. Repeated calls
     * within the debounce window collapse into a single run, and a request arriving while a
     * compilation is in flight schedules one more run after it.
     */
    void scheduleCompile();

    /**
     * Records an edit and requests a recompilation once the user has stopped typing for the quiet
     * period. Each further edit pushes the run back, so a compilation never starts mid-edit.
     */
    void scheduleCompileAfterEdit();
}
