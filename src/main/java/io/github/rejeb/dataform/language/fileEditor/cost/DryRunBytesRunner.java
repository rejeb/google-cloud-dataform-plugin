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
package io.github.rejeb.dataform.language.fileEditor.cost;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Runs a BigQuery dry-run of a query to learn how many bytes it would process.
 */
public interface DryRunBytesRunner {

    /**
     * Dry-runs the query; blocks until BigQuery answers and never throws.
     *
     * @param billingProject project the dry-run job is created in
     * @param location       location of the job, null to let BigQuery choose
     * @param sql            the query, as it would be executed
     */
    @NotNull
    CostEstimate run(@NotNull String billingProject, @Nullable String location, @NotNull String sql);
}
