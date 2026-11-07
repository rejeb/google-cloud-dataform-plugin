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
import java.util.Map;
import java.util.TreeSet;

/**
 * A branch of a Git remote holding the given files, with no Git behind it.
 */
public final class FakeGitRemoteBranch implements GitRemoteBranch {

    private final String name;
    private final Map<String, String> files;
    private RuntimeException fetchFailure;
    private int fetches;

    public FakeGitRemoteBranch(@NotNull String name, @NotNull Map<String, String> files) {
        this.name = name;
        this.files = files;
    }

    public void failFetchWith(@NotNull RuntimeException failure) {
        fetchFailure = failure;
    }

    public int fetches() {
        return fetches;
    }

    @Override
    public @NotNull String name() {
        return name;
    }

    @Override
    public @NotNull String trackingName() {
        return "origin/" + name;
    }

    @Override
    public void fetch() {
        fetches++;
        if (fetchFailure != null) {
            throw fetchFailure;
        }
    }

    @Override
    public @NotNull List<String> listFiles() {
        return List.copyOf(new TreeSet<>(files.keySet()));
    }

    @Override
    public @NotNull String readFile(@NotNull String path) {
        return files.get(path);
    }
}
