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

import com.google.api.gax.rpc.NotFoundException;
import com.google.api.gax.rpc.PermissionDeniedException;
import com.google.api.gax.rpc.StatusCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GcpApiErrorsTest {

    static StatusCode code(StatusCode.Code value) {
        return new StatusCode() {
            @Override
            public Code getCode() {
                return value;
            }

            @Override
            public Object getTransportCode() {
                return value.getHttpStatusCode();
            }
        };
    }

    @Test
    void aWrappedNotFoundIsNotFound() {
        NotFoundException notFound = new NotFoundException(new RuntimeException("repository"), code(StatusCode.Code.NOT_FOUND), false);

        assertTrue(GcpApiErrors.isNotFound(new GcpApiException("Failed to list workspaces", notFound)));
    }

    @Test
    void otherFailuresAreNotNotFound() {
        PermissionDeniedException denied = new PermissionDeniedException(new RuntimeException("iam"), code(StatusCode.Code.PERMISSION_DENIED), false);

        assertFalse(GcpApiErrors.isNotFound(new GcpApiException("Failed to list workspaces", denied)));
        assertFalse(GcpApiErrors.isNotFound(new GcpApiException("Failed")));
        assertFalse(GcpApiErrors.isNotFound(null));
    }
}
