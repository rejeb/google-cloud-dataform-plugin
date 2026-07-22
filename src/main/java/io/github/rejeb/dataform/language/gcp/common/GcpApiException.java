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

import io.github.rejeb.dataform.language.gcp.auth.AuthTrigger;
import io.github.rejeb.dataform.language.gcp.auth.GcpAuthErrors;

public class GcpApiException extends RuntimeException {

    /**
     * Wraps a GCP SDK failure. Every repository of the plugin funnels its failures here, so this
     * is also where a credential rejected by the server is turned into a sign-in request.
     *
     * @param message human-readable description of the GCP API failure
     * @param cause   the underlying exception from the GCP SDK
     */
    public GcpApiException(String message, Throwable cause) {
        super(message, cause);
        GcpAuthErrors.reportIfAuthFailure(cause, AuthTrigger.USER_ACTION);
    }

    public GcpApiException(String message) {
        super(message);
    }
}
