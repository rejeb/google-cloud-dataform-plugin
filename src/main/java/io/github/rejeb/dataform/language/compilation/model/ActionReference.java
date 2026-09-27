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
package io.github.rejeb.dataform.language.compilation.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * What a {@code ref()} call designates: a name, optionally narrowed by the schema and the database
 * the action lives in.
 *
 * @param database the database, {@code null} when the call leaves it out
 * @param schema   the schema, {@code null} when the call leaves it out
 * @param name     the name of the action
 */
public record ActionReference(@Nullable String database, @Nullable String schema, @NotNull String name) {

    /**
     * A reference by name alone.
     *
     * @param name the name of the action
     * @return the reference
     */
    public static @NotNull ActionReference named(@NotNull String name) {
        return new ActionReference(null, null, name);
    }

    /**
     * Whether a target is the one designated, every part the reference leaves out matching anything.
     *
     * @param target the target to test, {@code null} matching nothing
     * @return whether the target is designated
     */
    public boolean matches(@Nullable Target target) {
        return target != null
                && name.equals(target.getName())
                && (schema == null || schema.equals(target.getSchema()))
                && (database == null || database.equals(target.getDatabase()));
    }
}
