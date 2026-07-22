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
package io.github.rejeb.dataform.language.gcp.auth;

import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Persistent, IDE wide storage for the plugin owned Google credential. Shared by every project
 * and window of the running IDE, and by other IDEs when the platform keystore is OS backed.
 */
public interface DataformCredentialsStore {

    /**
     * @return the stored credential, or empty when the user never signed in
     */
    @NotNull
    Optional<StoredCredentials> load();

    /**
     * @param credentials credential to persist
     */
    void save(@NotNull StoredCredentials credentials);

    /**
     * Removes the stored credential.
     */
    void clear();
}
