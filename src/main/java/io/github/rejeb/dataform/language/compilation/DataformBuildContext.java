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
package io.github.rejeb.dataform.language.compilation;

import com.intellij.openapi.project.Project;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class DataformBuildContext {

    private static final com.intellij.openapi.util.Key<Semaphore> BUILD_SEMAPHORE_KEY =
            com.intellij.openapi.util.Key.create("DATAFORM_BUILD_SEMAPHORE_KEY");

    public final Project project;
    public final long started;
    public volatile long finished;

    public final AtomicInteger errors = new AtomicInteger(0);
    public final CompletableFuture<DataformBuildResult> result = new CompletableFuture<>();

    private final Semaphore buildSemaphore;

    public DataformBuildContext(Project project) {
        this.project = project;
        this.started = System.currentTimeMillis();
        this.finished = started;
        Semaphore existing = project.getUserData(BUILD_SEMAPHORE_KEY);
        if (existing != null) {
            this.buildSemaphore = existing;
        } else {
            Semaphore newSem = new Semaphore(1);
            ((com.intellij.openapi.util.UserDataHolderEx) project)
                    .putUserDataIfAbsent(BUILD_SEMAPHORE_KEY, newSem);
            this.buildSemaphore = project.getUserData(BUILD_SEMAPHORE_KEY);
        }
    }

    public long getDuration() {
        return finished - started;
    }

    /** Bloque jusqu'à obtenir le sémaphore (un seul build à la fois). */
    public boolean waitAndStart() {
        while (true) {
            if (project.isDisposed()) {
                canceled();
                return false;
            }
            try {
                if (buildSemaphore.tryAcquire(100, TimeUnit.MILLISECONDS)) return true;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                canceled();
                return false;
            }
        }
    }

    public void finished(boolean isSuccess, String message) {
        finished = System.currentTimeMillis();
        buildSemaphore.release();
        result.complete(new DataformBuildResult(
                isSuccess, false, started, getDuration(), errors.get(), message
        ));
    }

    public void canceled() {
        finished = System.currentTimeMillis();
        result.complete(new DataformBuildResult(
                false, true, started, getDuration(), 0, "Dataform compile canceled"
        ));
    }
}