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
package io.github.rejeb.dataform.language.gcp.common;

import com.google.api.gax.rpc.StatusCode;
import com.google.api.gax.rpc.UnauthenticatedException;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.Mockito.mock;

/**
 * Every call to the Dataform API goes through one place, which retries once with a fresh credential
 * when the server rejects the current one and reports any other failure as a {@link GcpApiException}.
 */
public class DataformApiTest extends BasePlatformTestCase {

    public void testACallRejectedForItsCredentialIsRetriedOnceWithAFreshClient() {
        AtomicInteger opened = new AtomicInteger();
        String result = DataformApi.call(() -> {
            opened.incrementAndGet();
            return null;
        }, "unused", client -> {
            if (opened.get() == 1) throw new UnauthenticatedException("expired", null, mock(StatusCode.class), false);
            return "done";
        });

        assertEquals("done", result);
        assertEquals("a fresh client is opened for the retry", 2, opened.get());
    }

    public void testAnyOtherFailureIsReportedWithItsOwnCause() {
        IOException cause = new IOException("network down");
        GcpApiException thrown = null;
        try {
            DataformApi.call(() -> null, "Error listing workspaces.", client -> {
                throw cause;
            });
        } catch (GcpApiException e) {
            thrown = e;
        }

        assertNotNull(thrown);
        assertEquals("Error listing workspaces.", thrown.getMessage());
        assertSame("the SDK failure stays the cause, not a wrapper of it", cause, thrown.getCause());
    }

    public void testAFailureAlreadyDescribedIsLeftAsItIs() {
        GcpApiException described = new GcpApiException("Failed to write \"a.sqlx\" after 3 attempt(s).");
        try {
            DataformApi.call(() -> null, "Error syncing files.", client -> {
                throw described;
            });
            fail("the failure must propagate");
        } catch (GcpApiException e) {
            assertSame(described, e);
        }
    }
}
