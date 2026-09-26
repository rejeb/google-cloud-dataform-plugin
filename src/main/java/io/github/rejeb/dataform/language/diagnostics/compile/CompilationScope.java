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
package io.github.rejeb.dataform.language.diagnostics.compile;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * What the code at the place of a compilation error could have meant: the names its JavaScript
 * can read, the actions of the project, and the config properties allowed there.
 */
public interface CompilationScope {

    /**
     * The names JavaScript of the file can read: its own declarations, the includes and the
     * functions Dataform provides.
     */
    @NotNull Collection<String> jsNames();

    /**
     * The names of the actions of the project, which {@code ref()} accepts.
     */
    @NotNull Collection<String> actionNames();

    /**
     * The config properties allowed in the object the place is in.
     */
    @NotNull Collection<String> configKeys();

    /**
     * Whether the place is in the config block, which runs before and apart from the js blocks, so
     * nothing they declare can be read there.
     */
    default boolean isInConfig() {
        return false;
    }

    CompilationScope EMPTY = new CompilationScope() {
        @Override
        public @NotNull Collection<String> jsNames() {
            return List.of();
        }

        @Override
        public @NotNull Collection<String> actionNames() {
            return List.of();
        }

        @Override
        public @NotNull Collection<String> configKeys() {
            return List.of();
        }
    };
}
