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

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

class DataformCredentialsAccessTokenTest {

    private static final Date IN_ONE_HOUR = new Date(System.currentTimeMillis() + 3_600_000L);

    @Test
    void theTokenOfTheCredentialIsReturned() throws IOException {
        DataformCredentialsService service = serviceGiving(GoogleCredentials.create(new AccessToken("tok", IN_ONE_HOUR)));

        assertEquals("tok", service.accessToken(null));
    }

    @Test
    void aQuotaProjectKeepsTheToken() throws IOException {
        DataformCredentialsService service = serviceGiving(GoogleCredentials.create(new AccessToken("tok", IN_ONE_HOUR)));

        assertEquals("tok", service.accessToken("p"));
    }

    @Test
    void aCredentialWithoutTokenFails() {
        DataformCredentialsService service = serviceGiving(mock(GoogleCredentials.class));

        IOException failure = assertThrows(IOException.class, () -> service.accessToken(null));

        assertEquals("No access token", failure.getMessage());
    }

    private static DataformCredentialsService serviceGiving(GoogleCredentials credentials) {
        DataformCredentialsService service = mock(DataformCredentialsService.class);
        doReturn(credentials).when(service).get();
        try {
            doCallRealMethod().when(service).accessToken(any());
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        return service;
    }
}
