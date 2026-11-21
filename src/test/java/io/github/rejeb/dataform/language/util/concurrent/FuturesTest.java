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
package io.github.rejeb.dataform.language.util.concurrent;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FuturesTest {

    @Test
    void aCompletedFutureGivesItsValue() {
        assertEquals("value", Futures.await(CompletableFuture.completedFuture("value")));
    }

    @Test
    void aRuntimeFailureIsRethrownAsIs() {
        IllegalStateException failure = new IllegalStateException("boom");

        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> Futures.await(CompletableFuture.failedFuture(failure))));
    }

    @Test
    void anErrorIsRethrownAsIs() {
        AssertionError failure = new AssertionError("broken");

        assertSame(failure, assertThrows(AssertionError.class,
                () -> Futures.await(CompletableFuture.failedFuture(failure))));
    }

    @Test
    void nestedCompletionExceptionsAreUnwrapped() {
        IllegalStateException failure = new IllegalStateException("deep");
        CompletableFuture<String> future = CompletableFuture.failedFuture(
                new CompletionException(new CompletionException(failure)));

        assertSame(failure, assertThrows(IllegalStateException.class, () -> Futures.await(future)));
    }

    @Test
    void aCheckedFailureStaysInsideACompletionException() {
        IOException failure = new IOException("io");

        CompletionException thrown = assertThrows(CompletionException.class,
                () -> Futures.await(CompletableFuture.failedFuture(failure)));

        assertSame(failure, thrown.getCause());
    }
}
