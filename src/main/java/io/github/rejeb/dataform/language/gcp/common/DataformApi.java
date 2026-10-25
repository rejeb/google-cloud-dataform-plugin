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

import com.google.cloud.dataform.v1.DataformClient;
import com.intellij.openapi.progress.ProcessCanceledException;
import io.github.rejeb.dataform.language.gcp.auth.AuthTrigger;
import io.github.rejeb.dataform.language.gcp.auth.GcpCalls;
import io.github.rejeb.dataform.language.util.GcpClientsUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/**
 * Runs the calls of the plugin to the Dataform API. Each call gets a client of its own, is retried
 * once with a freshly resolved credential when the server rejects the current one, and has any
 * other failure reported as a {@link GcpApiException}, which raises the sign-in banner when the
 * credential is the cause.
 */
public final class DataformApi {

    /** A call made with a Dataform client. */
    @FunctionalInterface
    public interface Call<T> {
        T apply(@Nullable DataformClient client) throws Exception;
    }

    /** A call made with a Dataform client that returns nothing. */
    @FunctionalInterface
    public interface VoidCall {
        void apply(@Nullable DataformClient client) throws Exception;
    }

    @FunctionalInterface
    interface ClientOpener {
        DataformClient open() throws Exception;
    }

    private DataformApi() {
    }

    /**
     * @param projectId the project billed for the quota of the call
     * @param failure   the message of the failure, given the error the call raised
     * @param call      the call
     * @return what the call returned
     * @throws GcpApiException when the call fails
     */
    public static <T> T call(@Nullable String projectId, @NotNull Function<Throwable, String> failure,
                             @NotNull Call<T> call) {
        return call(() -> GcpClientsUtils.dataformClient(projectId), failure, call);
    }

    /**
     * @param projectId the project billed for the quota of the call
     * @param failure   the message of the failure
     * @param call      the call
     * @return what the call returned
     * @throws GcpApiException when the call fails
     */
    public static <T> T call(@Nullable String projectId, @NotNull String failure, @NotNull Call<T> call) {
        return call(projectId, error -> failure, call);
    }

    /**
     * @param projectId the project billed for the quota of the call
     * @param failure   the message of the failure
     * @param call      the call
     * @throws GcpApiException when the call fails
     */
    public static void run(@Nullable String projectId, @NotNull String failure, @NotNull VoidCall call) {
        run(projectId, error -> failure, call);
    }

    /**
     * @param projectId the project billed for the quota of the call
     * @param failure   the message of the failure, given the error the call raised
     * @param call      the call
     * @throws GcpApiException when the call fails
     */
    public static void run(@Nullable String projectId, @NotNull Function<Throwable, String> failure,
                           @NotNull VoidCall call) {
        call(projectId, failure, client -> {
            call.apply(client);
            return null;
        });
    }

    static <T> T call(@NotNull ClientOpener opener, @NotNull String failure, @NotNull Call<T> call) {
        return call(opener, error -> failure, call);
    }

    static <T> T call(@NotNull ClientOpener opener, @NotNull Function<Throwable, String> failure,
                      @NotNull Call<T> call) {
        try {
            return GcpCalls.execute(AuthTrigger.USER_ACTION, () -> {
                try (DataformClient client = opener.open()) {
                    return call.apply(client);
                }
            });
        } catch (GcpApiException | ProcessCanceledException e) {
            throw e;
        } catch (RuntimeException e) {
            Throwable cause = e.getClass() == RuntimeException.class && e.getCause() != null ? e.getCause() : e;
            if (cause instanceof GcpApiException api) throw api;
            throw new GcpApiException(failure.apply(cause), cause);
        }
    }
}
