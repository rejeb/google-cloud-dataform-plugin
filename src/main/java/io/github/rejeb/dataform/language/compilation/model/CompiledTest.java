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
package io.github.rejeb.dataform.language.compilation.model;

import io.github.rejeb.dataform.language.util.DataformPaths;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class CompiledTest {
    private String name;
    private String testQuery;
    private String expectedOutputQuery;
    private String fileName;
    private boolean disabled;
    private List<String> tags;
    private Target target;

    public String getName() {
        return name;
    }

    /**
     * Returns the query of the tested dataset with its inputs replaced by the mocked rows.
     */
    @NotNull
    public String getTestQuery() {
        return testQuery != null ? testQuery.trim() : "";
    }

    /**
     * Returns the query producing the rows the test query must return.
     */
    @NotNull
    public String getExpectedOutputQuery() {
        return expectedOutputQuery != null ? expectedOutputQuery.trim() : "";
    }

    /**
     * Returns the project-relative path of the test file with {@code /} separators.
     */
    @NotNull
    public String getFileName() {
        return fileName != null ? DataformPaths.normalize(fileName) : "";
    }

    public boolean isDisabled() {
        return disabled;
    }

    public List<String> getTags() {
        return tags != null ? tags : Collections.emptyList();
    }

    @Nullable
    public Target getTarget() {
        return target;
    }

    public boolean matchFileName(String fileName) {
        return DataformPaths.pointsTo(fileName, this.fileName);
    }
}
