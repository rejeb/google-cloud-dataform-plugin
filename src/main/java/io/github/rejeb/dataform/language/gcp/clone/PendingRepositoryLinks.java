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
package io.github.rejeb.dataform.language.gcp.clone;

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/**
 * Holds what a repository fetched from Google Cloud must be linked to until its project opens. The
 * project settings do not exist before that, so the link waits here, for the session only.
 */
public interface PendingRepositoryLinks {

    /**
     * @return the application level instance of this service
     */
    static PendingRepositoryLinks getInstance() {
        return ApplicationManager.getApplication().getService(PendingRepositoryLinks.class);
    }

    /**
     * Keeps the link of the repository fetched into {@code directory}.
     */
    void register(@NotNull Path directory, @NotNull RepositoryLink link);

    /**
     * Hands over the link waiting for a project directory.
     *
     * @return the link, {@code null} when none is waiting for this directory
     */
    @Nullable RepositoryLink take(@NotNull String projectDirectory);
}
