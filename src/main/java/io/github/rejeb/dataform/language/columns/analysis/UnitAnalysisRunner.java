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
package io.github.rejeb.dataform.language.columns.analysis;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Runs the analyses a column lineage build is made of. The build decides what each analysis is;
 * the runner decides on which threads and under which read actions they run.
 */
public interface UnitAnalysisRunner {

    /**
     * Runs the analyses one after the other on the calling thread, inside whatever read action the
     * caller holds.
     */
    UnitAnalysisRunner SEQUENTIAL = new UnitAnalysisRunner() {
        @Override
        public @NotNull <T> List<T> runAll(@NotNull List<? extends Supplier<T>> tasks) {
            List<T> results = new ArrayList<>(tasks.size());
            for (Supplier<T> task : tasks) {
                results.add(task.get());
            }
            return results;
        }
    };

    /**
     * Runs every task and returns their results in the order of the tasks.
     */
    @NotNull <T> List<T> runAll(@NotNull List<? extends Supplier<T>> tasks);
}
