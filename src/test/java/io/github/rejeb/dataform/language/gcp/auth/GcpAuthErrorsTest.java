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
import com.google.api.gax.rpc.StatusCode;
import com.google.api.gax.rpc.UnauthenticatedException;
import com.google.cloud.bigquery.BigQueryError;
import com.google.cloud.bigquery.BigQueryException;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GcpAuthErrorsTest {

    @Test
    public void nullIsNotAnAuthFailure() {
        assertFalse(GcpAuthErrors.isAuthFailure(null));
    }

    @Test
    public void explicitAuthRequiredIsAnAuthFailure() {
        assertTrue(GcpAuthErrors.isAuthFailure(new GcpAuthRequiredException("sign in")));
    }

    @Test
    public void unauthenticatedApiCallIsAnAuthFailure() {
        assertTrue(GcpAuthErrors.isAuthFailure(
                new UnauthenticatedException("nope", null, mock(StatusCode.class), false)));
    }

    @Test
    public void permissionDeniedIsOnlyAnAuthFailureForRejectedTokens() {
        assertFalse(GcpAuthErrors.isAuthFailure(
                new PermissionDeniedException("caller lacks dataform.repositories.get", null,
                        mock(StatusCode.class), false)));
        assertTrue(GcpAuthErrors.isAuthFailure(
                new PermissionDeniedException("Request had invalid authentication credentials: invalid_token",
                        null, mock(StatusCode.class), false)));
    }

    @Test
    public void bigQueryUnauthorizedIsAnAuthFailure() {
        assertTrue(GcpAuthErrors.isAuthFailure(new BigQueryException(401, "unauthorized")));
    }

    @Test
    public void bigQueryForbiddenIsAnAuthFailureOnlyWithTheAuthErrorReason() {
        assertTrue(GcpAuthErrors.isAuthFailure(new BigQueryException(403, "denied",
                new BigQueryError("authError", "global", "denied"))));
        assertFalse(GcpAuthErrors.isAuthFailure(new BigQueryException(403, "denied",
                new BigQueryError("accessDenied", "global", "denied"))));
        assertFalse(GcpAuthErrors.isAuthFailure(new BigQueryException(403, "denied")));
        assertFalse(GcpAuthErrors.isAuthFailure(new BigQueryException(500, "boom")));
    }

    @Test
    public void googleJsonResponseIsAnAuthFailureOnlyFor401() {
        GoogleJsonResponseException unauthorized = mock(GoogleJsonResponseException.class);
        when(unauthorized.getStatusCode()).thenReturn(401);
        assertTrue(GcpAuthErrors.isAuthFailure(unauthorized));

        GoogleJsonResponseException notFound = mock(GoogleJsonResponseException.class);
        when(notFound.getStatusCode()).thenReturn(404);
        assertFalse(GcpAuthErrors.isAuthFailure(notFound));
    }

    @Test
    public void oauthMessagesAreAnAuthFailureWhateverTheType() {
        assertTrue(GcpAuthErrors.isAuthFailure(new IOException("Error getting access token: invalid_grant")));
        assertTrue(GcpAuthErrors.isAuthFailure(new RuntimeException("INVALID_TOKEN")));
        assertFalse(GcpAuthErrors.isAuthFailure(new IOException("connection reset")));
        assertFalse(GcpAuthErrors.isAuthFailure(new RuntimeException((String) null)));
    }

    @Test
    public void walksTheCauseChain() {
        Throwable wrapped = new RuntimeException("wrapper",
                new IllegalStateException("again", new BigQueryException(401, "unauthorized")));
        assertTrue(GcpAuthErrors.isAuthFailure(wrapped));
        assertFalse(GcpAuthErrors.isAuthFailure(new RuntimeException("wrapper", new IOException("io"))));
    }
}
