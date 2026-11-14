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
import org.jetbrains.annotations.Nullable;

/**
 * What moved data along a recorded link: a query, a load job, a pipeline.
 *
 * @param name        the resource name of the process
 * @param displayName the name shown to the user, never empty
 * @param origin      the system that reported the process, such as {@code BIGQUERY}
 * @param latestJobId the id of the latest BigQuery job of the process, when it reports one
 */
public record RecordedProcess(@NotNull String name, @NotNull String displayName, @NotNull String origin,
                              @Nullable String latestJobId) {
}
