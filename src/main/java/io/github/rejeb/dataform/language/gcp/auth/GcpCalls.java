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

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Callable;

/**
 * Runs a GCP call so that a credential rejected by the server is detected, dropped and retried
 * once with a freshly resolved credential before the editor banner is raised.
 */
public final class GcpCalls {

    private static final Logger LOG = Logger.getInstance(GcpCalls.class);

    private GcpCalls() {
    }

    /**
     * @param trigger whether the call was started by an explicit user action
     * @param call    the GCP call to run
     * @param <T>     result type of the call
     * @return the call result
     */
    public static <T> T execute(@NotNull AuthTrigger trigger, @NotNull Callable<T> call) {
        try {
            return doCall(call);
        } catch (Exception first) {
            if (!GcpAuthErrors.isAuthFailure(first)) {
                throw asRuntime(first);
            }
            LOG.info("GCP call rejected the current credential, retrying with a fresh one.");
            DataformCredentialsService.getInstance().invalidate();
            try {
                return doCall(call);
            } catch (Exception second) {
                GcpAuthErrors.reportIfAuthFailure(second, trigger);
                throw asRuntime(second);
            }
        }
    }

    private static <T> T doCall(Callable<T> call) throws Exception {
        return call.call();
    }

    private static RuntimeException asRuntime(Exception e) {
        return e instanceof RuntimeException runtime ? runtime : new RuntimeException(e);
    }
}
