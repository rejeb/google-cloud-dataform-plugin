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
package io.github.rejeb.dataform.language.setup;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Moves the Dataform core version declared by {@code dataformCoreVersion} in
 * {@code workflow_settings.yaml} to the {@code @dataform/core} dependency of {@code package.json}.
 */
public interface DataformCoreVersionMigrator {

    String CORE_VERSION_KEY = "dataformCoreVersion";

    static DataformCoreVersionMigrator getInstance(@NotNull Project project) {
        return project.getService(DataformCoreVersionMigrator.class);
    }

    /**
     * Returns the {@code dataformCoreVersion} declared in the given {@code workflow_settings.yaml}.
     * Must be called under a read action.
     */
    Optional<String> findDeclaredCoreVersion(@NotNull VirtualFile workflowSettings);

    /**
     * Declares the core version in {@code package.json}, keeping a version it already declares,
     * removes {@code dataformCoreVersion} from {@code workflow_settings.yaml}, saves both files and
     * installs the packages. Must be called on the EDT.
     *
     * @return {@code false} when nothing was migrated
     */
    boolean migrate(@NotNull VirtualFile workflowSettings);
}
