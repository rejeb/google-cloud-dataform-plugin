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

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.gax.rpc.PermissionDeniedException;
import com.google.api.gax.rpc.UnauthenticatedException;
import com.google.cloud.bigquery.BigQueryException;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public final class GcpAuthErrors {

    private static final String AUTH_ERROR_REASON = "autherror";
    private static final String INVALID_GRANT = "invalid_grant";
    private static final String INVALID_TOKEN = "invalid_token";
    private static final String GOOGLE_AUTH_EXCEPTION = "GoogleAuthException";
    private static final String TOKEN_RESPONSE_EXCEPTION = "TokenResponseException";

    private GcpAuthErrors() {
    }

    /**
     * Tells whether the given failure means the current Google credential was rejected by the
     * server and must be renewed. Genuine IAM denials are deliberately not matched.
     *
     * @param throwable failure raised by a GCP client call, may be {@code null}
     * @return {@code true} when the credential itself is no longer accepted
     */
    public static boolean isAuthFailure(@Nullable Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (matches(current)) {
                return true;
            }
            if (current.getCause() == current) {
                break;
            }
        }
        return false;
    }

    /**
     * Marks the shared authentication state as requiring a sign-in when the given failure is a
     * credential rejection. Does nothing otherwise.
     *
     * @param throwable failure raised by a GCP client call, may be {@code null}
     * @param trigger   whether the failing call was started by an explicit user action
     * @return {@code true} when the state was switched to {@link AuthStatus#REQUIRED}
     */
    public static boolean reportIfAuthFailure(@Nullable Throwable throwable, AuthTrigger trigger) {
        if (!isAuthFailure(throwable)) {
            return false;
        }
        DataformCredentialsService.getInstance().invalidate();
        DataformAuthState.getInstance().markAuthRequired(trigger);
        return true;
    }

    private static boolean matches(Throwable throwable) {
        return switch (throwable) {
            case GcpAuthRequiredException ignored -> true;
            case UnauthenticatedException ignored -> true;
            case PermissionDeniedException ignored -> isOauthMessage(throwable.getMessage());
            case GoogleJsonResponseException e -> e.getStatusCode() == 401;
            case BigQueryException e -> isBigQueryAuthFailure(e);
            default -> isOauthType(throwable) || isOauthMessage(throwable.getMessage());
        };
    }

    private static boolean isOauthType(Throwable throwable) {
        String name = throwable.getClass().getSimpleName();
        return GOOGLE_AUTH_EXCEPTION.equals(name) || TOKEN_RESPONSE_EXCEPTION.equals(name);
    }

    private static boolean isBigQueryAuthFailure(BigQueryException e) {
        if (e.getCode() == 401) {
            return true;
        }
        return e.getCode() == 403
                && e.getReason() != null
                && AUTH_ERROR_REASON.equals(e.getReason().toLowerCase(Locale.ROOT));
    }

    private static boolean isOauthMessage(@Nullable String message) {
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        return lower.contains(INVALID_GRANT) || lower.contains(INVALID_TOKEN);
    }
}
