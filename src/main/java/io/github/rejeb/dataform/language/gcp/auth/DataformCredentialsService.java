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

import com.google.auth.oauth2.GoogleCredentials;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Owns the Google credential used by every GCP call of the plugin. Resolution is always silent:
 * no sign-in is ever started without an explicit user action through {@link #signIn(Project)}.
 */
public interface DataformCredentialsService {

    /**
     * @return the application level instance of this service
     */
    static DataformCredentialsService getInstance() {
        return ApplicationManager.getApplication().getService(DataformCredentialsService.class);
    }

    /**
     * Resolves a usable credential from the in-memory cache, then from the shared store, then from
     * the gcloud application default credentials as a read-only fallback. The fallback is disabled
     * once the user explicitly signed out, until the next successful sign-in.
     *
     * @return a usable credential
     * @throws GcpAuthRequiredException when the user must sign in
     */
    @NotNull
    GoogleCredentials get();

    /**
     * Drops the in-memory credential so the next {@link #get()} re-reads the shared store.
     */
    void invalidate();

    /**
     * @return {@code true} when a credential is available without any user interaction
     */
    boolean isSignedIn();

    /**
     * @return the signed-in account email, or {@code null} when unknown
     */
    @Nullable
    String getAccountEmail();

    /**
     * Starts the interactive sign-in in the background. Must only be called from an explicit user
     * action. Concurrent calls join the running flow instead of opening a second browser tab.
     *
     * @param project project used to host the progress indicator, may be {@code null}
     */
    void signIn(@Nullable Project project);

    /**
     * Forgets the credential, both in memory and in the shared store, and stops falling back to the
     * gcloud application default credentials until the next successful sign-in.
     */
    void signOut();
}
