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
 * What the run of the query of an action cost, read from its BigQuery job.
 *
 * @param sql            the compiled SQL of the action when it ran
 * @param bytesProcessed bytes the job processed
 * @param bytesBilled    bytes the job was billed
 * @param location       location the job ran in, null when unknown
 */
public record RunCost(@NotNull String sql, long bytesProcessed, long bytesBilled, @Nullable String location) {

    /**
     * Tells whether the action no longer compiles to the SQL this run measured.
     *
     * @param currentSql the compiled SQL of the action now, null when unknown
     */
    public boolean outdatedFor(@Nullable String currentSql) {
        return !sql.equals(currentSql);
    }
}
