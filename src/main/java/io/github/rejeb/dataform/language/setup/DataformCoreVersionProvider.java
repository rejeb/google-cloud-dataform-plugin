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
package io.github.rejeb.dataform.language.setup;

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Resolves the {@code @dataform/core} version proposed to new Dataform projects.
 */
public interface DataformCoreVersionProvider {

    /**
     * Version proposed when the latest published version cannot be resolved.
     */
    String FALLBACK_VERSION = "3.0.56";

    static DataformCoreVersionProvider getInstance() {
        return ApplicationManager.getApplication().getService(DataformCoreVersionProvider.class);
    }

    /**
     * Queries the npm registry for the latest published {@code @dataform/core} version.
     * Performs network I/O: never call it on the EDT or under a read action.
     *
     * @return empty when the registry cannot be reached or its answer cannot be read
     */
    Optional<String> fetchLatestVersion();

    /**
     * Returns the latest version already fetched during this session, or {@link #FALLBACK_VERSION}.
     * Never performs I/O.
     */
    @NotNull
    String knownLatestVersion();
}
