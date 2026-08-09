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
package io.github.rejeb.dataform.language.schema.sql;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class DryRunErrorRegistryImpl implements DryRunErrorRegistry {

    private final Map<String, String> errors = new ConcurrentHashMap<>();

    @Override
    public void report(@NotNull String actionFullName, @NotNull String message) {
        errors.put(actionFullName, message);
    }

    @Override
    public void clear(@NotNull String actionFullName) {
        errors.remove(actionFullName);
    }

    @Override
    public void retainOnly(@NotNull Set<String> actionFullNames) {
        if (actionFullNames.isEmpty()) return;
        errors.keySet().retainAll(actionFullNames);
    }

    @Override
    public @Nullable String getError(@NotNull String actionFullName) {
        return errors.get(actionFullName);
    }

    @Override
    public @NotNull Map<String, String> getErrors() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(errors));
    }
}
