/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
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

import java.util.Collections;
import java.util.List;

public class CompiledOperation {
    private Target target;
    private List<String> queries;
    private boolean disabled;
    private String fileName;
    private List<String> tags;
    private List<Target> dependencyTargets;
    private Target canonicalTarget;
    private boolean hasOutput;

    public static class Target {
        private String schema;
        private String name;
        private String database;

        public String getSchema() {
            return schema;
        }

        public String getName() {
            return name;
        }

        public String getDatabase() {
            return database;
        }
    }

    public Target getTarget() {
        return target;
    }

    public List<String> getQueries() {
        return queries != null ? queries : Collections.emptyList();
    }

    public boolean isDisabled() {
        return disabled;
    }

    public String getFileName() {
        return fileName;
    }

    public boolean isHasOutput() {
        return hasOutput;
    }
}
