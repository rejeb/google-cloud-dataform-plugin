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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GcpConfigProviderTest {

    @Test
    void requireCoordinatesReturnsTheConfiguredRepository() {
        assertEquals(new RepositoryCoordinates("p", "eu", "r"),
                GcpConfigProvider.requireCoordinatesOf(new StubProvider("p", "eu", "r")));
    }

    @Test
    void requireCoordinatesFailsWithoutAConfiguredRepository() {
        GcpApiException failure = assertThrows(GcpApiException.class,
                () -> GcpConfigProvider.requireCoordinatesOf(new StubProvider("p", "eu", null)));

        assertEquals("No active repository config — configure a repository first.", failure.getMessage());
    }

    private record StubProvider(@Nullable String getProjectId, @Nullable String getLocation,
                                @Nullable String getRepositoryId) implements GcpConfigProvider {

        @Override
        public @NotNull String getServiceAccount() {
            return "";
        }

        @Override
        public @NotNull CommitAuthorConfig getCommitAuthor() {
            return new CommitAuthorConfig("n", "e@x");
        }
    }
}
