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
package io.github.rejeb.dataform.language.gcp.workspace.repository;

import com.intellij.util.concurrency.AppExecutorUtil;
import io.github.rejeb.dataform.language.util.concurrent.Futures;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * Reads every Dataform file of a {@link FileSource}. Directories are walked recursively because the
 * API lists one level at a time.
 *
 * <p>Every request is a network round trip, so the subdirectories of a level are listed together
 * and the files are read together, on an executor of their own: the walk never waits inside it for
 * another of its tasks, and it never borrows the JVM's common pool, which other code shares.</p>
 */
final class WorkspaceFileReader {

    private static final int READ_PARALLELISM = 8;
    private static final ExecutorService READ_EXECUTOR =
            AppExecutorUtil.createBoundedApplicationPoolExecutor("Dataform workspace read", READ_PARALLELISM);

    private WorkspaceFileReader() {
    }

    @NotNull
    static List<String> listAll(@NotNull FileSource source) {
        return Futures.await(walk("", source));
    }

    @NotNull
    static Map<String, String> readAll(@NotNull FileSource source) {
        return readEach(listAll(source), source);
    }

    /**
     * The files under a directory, in the order the listings give them. A level is listed, then its
     * subdirectories all at once, and a level waits for its subdirectories by composition rather
     * than by blocking a thread of the executor.
     */
    @NotNull
    private static CompletableFuture<List<String>> walk(@NotNull String directory, @NotNull FileSource source) {
        return CompletableFuture.supplyAsync(() -> source.list(directory), READ_EXECUTOR)
                .thenCompose(entries -> {
                    List<CompletableFuture<List<String>>> parts = new ArrayList<>(entries.size());
                    for (FileSource.Entry entry : entries) {
                        parts.add(entry.directory()
                                ? walk(entry.path(), source)
                                : CompletableFuture.completedFuture(List.of(entry.path())));
                    }
                    return CompletableFuture.allOf(parts.toArray(CompletableFuture[]::new))
                            .thenApply(ignored -> {
                                List<String> paths = new ArrayList<>();
                                for (CompletableFuture<List<String>> part : parts) {
                                    paths.addAll(part.join());
                                }
                                return paths;
                            });
                });
    }

    @NotNull
    private static Map<String, String> readEach(@NotNull List<String> paths, @NotNull FileSource source) {
        List<CompletableFuture<String>> contents = new ArrayList<>(paths.size());
        for (String path : paths) {
            contents.add(CompletableFuture.supplyAsync(() -> source.read(path), READ_EXECUTOR));
        }
        Map<String, String> files = new LinkedHashMap<>();
        try {
            for (int i = 0; i < paths.size(); i++) {
                files.put(paths.get(i), Futures.await(contents.get(i)));
            }
        } catch (RuntimeException e) {
            contents.forEach(content -> content.cancel(false));
            throw e;
        }
        return files;
    }
}
