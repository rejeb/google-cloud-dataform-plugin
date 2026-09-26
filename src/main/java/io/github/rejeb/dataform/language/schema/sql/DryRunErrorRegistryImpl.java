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
import java.util.concurrent.atomic.AtomicLong;

public final class DryRunErrorRegistryImpl implements DryRunErrorRegistry {

    private final Map<String, DryRunFailure> failures = new ConcurrentHashMap<>();
    private final AtomicLong modificationCount = new AtomicLong();

    @Override
    public void reportFailure(@NotNull String actionFullName, @NotNull DryRunFailure failure) {
        DryRunFailure previous = failures.put(actionFullName, failure);
        if (!failure.sameAs(previous)) modificationCount.incrementAndGet();
    }

    @Override
    public void clear(@NotNull String actionFullName) {
        if (failures.remove(actionFullName) != null) modificationCount.incrementAndGet();
    }

    @Override
    public void retainOnly(@NotNull Set<String> actionFullNames) {
        if (actionFullNames.isEmpty()) return;
        if (failures.keySet().retainAll(actionFullNames)) modificationCount.incrementAndGet();
    }

    @Override
    public @Nullable String getError(@NotNull String actionFullName) {
        DryRunFailure failure = failures.get(actionFullName);
        return failure == null ? null : failure.message();
    }

    @Override
    public @Nullable DryRunFailure getFailure(@NotNull String actionFullName) {
        return failures.get(actionFullName);
    }

    @Override
    public @NotNull Map<String, String> getErrors() {
        Map<String, String> errors = new LinkedHashMap<>();
        failures.forEach((name, failure) -> errors.put(name, failure.message()));
        return Collections.unmodifiableMap(errors);
    }

    @Override
    public long getModificationCount() {
        return modificationCount.get();
    }
}
