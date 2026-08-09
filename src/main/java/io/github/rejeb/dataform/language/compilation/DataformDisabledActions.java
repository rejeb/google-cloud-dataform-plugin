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
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * Tells whether the Dataform action defined in a source file declares {@code disabled: true}.
 * Answers come from the last compiled graph, so they are only as fresh as the last compilation.
 */
public interface DataformDisabledActions {

    static DataformDisabledActions getInstance(@NotNull Project project) {
        return project.getService(DataformDisabledActions.class);
    }

    /**
     * Whether every Dataform action defined in the given file is disabled. Returns {@code false}
     * for a file that defines no action, or when no compiled graph is available yet.
     */
    boolean isDisabled(@NotNull VirtualFile file);
}
