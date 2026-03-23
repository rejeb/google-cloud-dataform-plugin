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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

public class CompiledTable {
    private String type;
    private Target target;
    private String query;
    private boolean disabled;
    private String fileName;
    private List<String> tags;
    private ActionDescriptor actionDescriptor;
    private List<Target> dependencyTargets;
    private String hermeticity;
    private Target canonicalTarget;
    private boolean materialized = false;
    private String enumType;
    private List<String> preOps = new ArrayList<>();
    private List<String> postOps = new ArrayList<>();
    private List<String> incrementalPreOps = new ArrayList<>();
    private String incrementalQuery;


    public static class ActionDescriptor {
        private String description;

        public String getDescription() {
            return description;
        }
    }

    public String getType() {
        return materialized ? "materialized_view" : "view";
    }

    public Target getTarget() {
        return target;
    }

    public String getQuery() {
        return query.trim();
    }

    public CompiledQuery getQueries() {
        List<String> preOpsQueries = Stream.concat(preOps.stream(), incrementalPreOps.stream()).toList();

        return new CompiledQuery(this.getTarget().getFullName(),preOpsQueries, query, postOps, null);
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

    public ActionDescriptor getActionDescriptor() {
        return actionDescriptor;
    }

    public List<Target> getDependencyTargets() {
        return dependencyTargets != null ? dependencyTargets : Collections.emptyList();
    }

    public String getHermeticity() {
        return hermeticity;
    }

    public Target getCanonicalTarget() {
        return canonicalTarget;
    }

    public String getEnumType() {
        return enumType;
    }

    public boolean isMaterialized() {
        return materialized;
    }

    public List<String> getPreOps() {
        return preOps;
    }

    public List<String> getPostOps() {
        return postOps;
    }

    public List<String> getIncrementalPreOps() {
        return incrementalPreOps;
    }

    public String getIncrementalQuery() {
        return incrementalQuery;
    }

    public boolean matchFileName(String fileName) {
        return fileName.endsWith(this.fileName.replace("\\", "/"));
    }
}
