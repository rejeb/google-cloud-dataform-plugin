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
package io.github.rejeb.dataform.language.gcp.workspace;

import org.jetbrains.annotations.NotNull;

/**
 * How the commits of the active repository reach Git. A project cloned from the Git remote of the
 * repository commits with Git, because a commit made through a Dataform workspace would leave the
 * branch and the history of the clone behind. Any other project commits through its workspace.
 *
 * @param repository the repository as Dataform describes it
 * @param localClone how the project relates to the Git remote of the repository
 */
public record CommitRoute(@NotNull DataformRepository repository, @NotNull LocalClone localClone) {

    /**
     * Whether the project is a local clone of the Git remote, and whether the IDE can work on it.
     */
    public enum LocalClone {
        NONE,
        WITH_GIT_PLUGIN,
        WITHOUT_GIT_PLUGIN
    }

    /**
     * @return whether commits are made and pushed through the Dataform workspace
     */
    public boolean commitsThroughWorkspace() {
        return localClone == LocalClone.NONE;
    }

    /**
     * @return whether the user should be told that cloning the repository lets them work with Git
     */
    public boolean suggestsCloning() {
        return repository.hasGitRemote() && localClone == LocalClone.NONE;
    }

    /**
     * @return whether the user should be told to commit with the Git of the IDE
     */
    public boolean handsOverToGit() {
        return localClone == LocalClone.WITH_GIT_PLUGIN;
    }
}
