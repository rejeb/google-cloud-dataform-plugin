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

import java.util.Locale;

/**
 * Where a recorded lineage is read: the project whose lineage is asked for, which is also the one
 * billed for the calls, and the location the jobs ran in.
 *
 * @param projectId the Google Cloud project
 * @param location  the location, in any case, such as {@code EU} or {@code us-central1}
 */
public record LineageScope(@NotNull String projectId, @NotNull String location) {

    /**
     * @return the resource name the Data Lineage API takes as parent, with the location in lower case
     */
    public @NotNull String parent() {
        return "projects/" + projectId + "/locations/" + location.toLowerCase(Locale.ROOT);
    }
}
