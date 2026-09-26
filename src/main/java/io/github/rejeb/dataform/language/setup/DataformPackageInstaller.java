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

/**
 * Installs the npm packages declared in the {@code package.json} of a Dataform project.
 */
public interface DataformPackageInstaller {

    static DataformPackageInstaller getInstance(@NotNull Project project) {
        return project.getService(DataformPackageInstaller.class);
    }

    /**
     * Runs {@code dataform install} in the given directory as a background task and reports the
     * outcome with a notification.
     */
    void installAsync(@NotNull VirtualFile projectDir);
}
