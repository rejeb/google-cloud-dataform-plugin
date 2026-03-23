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
package io.github.rejeb.dataform.language.gcp.service;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

@State(
        name = "DataformGcpFileCache",
        storages = @Storage("dataform-gcp-file-cache.xml")
)
public final class DataformGcpFileCacheImpl
        implements DataformGcpFileCache, PersistentStateComponent<DataformGcpFileCacheImpl.State> {

    private State state = new State();

    @Override
    public @Nullable State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State loaded) {
        this.state = loaded;
    }

    @Override
    @NotNull
    public List<String> getCachedFiles() {
        return state.files != null ? List.copyOf(state.files) : List.of();
    }

    @Override
    public void update(@NotNull List<String> files) {
        state.files = List.copyOf(files);
    }

    @Override
    public void invalidate() {
        state.files = null;
    }

    public static final class State {
        @Nullable
        public List<String> files;
    }
}
