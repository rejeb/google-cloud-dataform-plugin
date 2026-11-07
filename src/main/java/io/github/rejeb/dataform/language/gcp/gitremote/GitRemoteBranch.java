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

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A branch of a Git remote as a local clone knows it. Reading it never touches the working tree or
 * the local branches. Every method but the names runs Git: must be called off the EDT.
 */
public interface GitRemoteBranch {

    /**
     * The name of the branch on the remote, such as {@code main}.
     */
    @NotNull String name();

    /**
     * The name of the branch in the clone, such as {@code origin/main}.
     */
    @NotNull String trackingName();

    /**
     * Brings the branch up to date with the remote, with the Git credentials already stored and no
     * prompt.
     *
     * @throws GitRemoteBranchException when the remote cannot be reached
     */
    void fetch();

    /**
     * The paths of the files of the branch, relative to its root and separated by {@code /}.
     *
     * @throws GitRemoteBranchException when Git fails
     */
    @NotNull List<String> listFiles();

    /**
     * The content of a file of the branch, read as UTF-8.
     *
     * @throws GitRemoteBranchException when Git fails or the branch has no such file
     */
    @NotNull String readFile(@NotNull String path);
}
