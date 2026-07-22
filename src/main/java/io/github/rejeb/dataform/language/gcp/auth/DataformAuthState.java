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

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Shared, application wide sign-in state driving the editor banner. Never starts a sign-in by
 * itself: it only records that one is needed so the user can decide.
 */
public interface DataformAuthState {

    /**
     * @return the application level instance of this service
     */
    static DataformAuthState getInstance() {
        return ApplicationManager.getApplication().getService(DataformAuthState.class);
    }

    /**
     * @return the current sign-in state
     */
    @NotNull
    AuthStatus getStatus();

    /**
     * @return {@code true} when the editor banner must be shown
     */
    boolean isBannerVisible();

    /**
     * @return the last sign-in error message, or {@code null} when there is none
     */
    @Nullable
    String getLastError();

    /**
     * Records that a Google credential is needed. A {@link AuthTrigger#USER_ACTION} trigger
     * re-arms a banner that the user previously closed.
     *
     * @param trigger origin of the failing operation
     */
    void markAuthRequired(@NotNull AuthTrigger trigger);

    /**
     * Records that the user started the sign-in flow.
     */
    void markSignInStarted();

    /**
     * Records that the sign-in flow failed or was cancelled.
     *
     * @param message message to display in the banner, may be {@code null}
     */
    void markSignInFailed(@Nullable String message);

    /**
     * Records a successful sign-in and hides the banner in every open project.
     */
    void markAuthenticated();

    /**
     * Hides the banner until the next failing user action.
     */
    void dismissBanner();
}
