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
package io.github.rejeb.dataform.language.gcp.gitremote;

import io.github.rejeb.dataform.language.gcp.workspace.DataformRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Finds the branches of the Git remote a Dataform repository is linked to in the local clone of the
 * project. Dataform does not serve the files of such a repository without a workspace. Only
 * registered when the Git plugin is installed.
 */
public interface GitRemoteBranches {

    /**
     * The default branch of the Git remote of {@code repository}. Runs Git: must be called off the EDT.
     *
     * @return the branch, {@code null} when the project is not a clone of that remote or has not
     * fetched that branch
     */
    @Nullable GitRemoteBranch find(@NotNull DataformRepository repository);
}
