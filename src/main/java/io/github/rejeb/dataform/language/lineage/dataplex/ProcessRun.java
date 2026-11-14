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
package io.github.rejeb.dataform.language.lineage.dataplex;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * One run with what ran it and the data it moved.
 *
 * @param run        the run
 * @param process    the process the run belongs to
 * @param sourceFqns the fully qualified names of the entities the process read, among the links asked for
 * @param targetFqn  the fully qualified name of the entity the process wrote
 */
public record ProcessRun(@NotNull RecordedRun run, @NotNull RecordedProcess process, @NotNull List<String> sourceFqns,
                         @NotNull String targetFqn) {

    public ProcessRun {
        sourceFqns = List.copyOf(sourceFqns);
    }
}
