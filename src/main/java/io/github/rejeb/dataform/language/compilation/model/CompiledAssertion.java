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

public class CompiledAssertion {
    private Target target;
    private String query;
    private boolean disabled;
    private String fileName;
    private List<String> tags;
    private List<Target> dependencyTargets;
    private Target canonicalTarget;

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

    public String getQuery() {
        return query;
    }

    public boolean isDisabled() {
        return disabled;
    }

    public String getFileName() {
        return fileName;
    }

    public List<String> getTags() {
        return tags != null ? tags : Collections.emptyList();
    }

    public List<Target> getDependencyTargets() {
        return dependencyTargets != null ? dependencyTargets : Collections.emptyList();
    }
}
