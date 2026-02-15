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


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class CompiledGraph {
    private List<CompiledTable> tables;
    private List<CompiledAssertion> assertions;
    private List<CompiledOperation> operations;
    private List<Declaration> declarations;
    private ProjectConfig projectConfig;
    private Map<String, Object> graphErrors;

    public List<CompiledTable> getTables() {
        return tables != null ? tables : Collections.emptyList();
    }

    public List<CompiledAssertion> getAssertions() {
        return assertions != null ? assertions : Collections.emptyList();
    }

    public List<CompiledOperation> getOperations() {
        return operations != null ? operations : Collections.emptyList();
    }

    public List<Declaration> getDeclarations() {
        return declarations != null ? declarations : Collections.emptyList();
    }

    public ProjectConfig getProjectConfig() {
        return projectConfig;
    }

    public Target findTableOrDeclaration(String name) {
        CompiledTable table = findTableByName(name);
        if (table != null && table.getTarget() != null) {
            return table.getTarget();
        }

        return getDeclarations().stream()
                .filter(d -> d.getTarget() != null && name.equals(d.getTarget().getName()))
                .map(Declaration::getTarget)
                .findFirst()
                .orElse(null);
    }

    public List<Target> getAllAvailableTables() {
        List<Target> allTables = new ArrayList<>();

        getTables().stream()
                .filter(t -> t.getTarget() != null)
                .map(CompiledTable::getTarget)
                .forEach(allTables::add);

        getDeclarations().stream()
                .filter(d -> d.getTarget() != null)
                .map(Declaration::getTarget)
                .forEach(allTables::add);

        return allTables;
    }

    public CompiledTable findTableByName(String name) {
        return getTables().stream()
                .filter(t -> t.getTarget() != null && name.equals(t.getTarget().getName()))
                .findFirst()
                .orElse(null);
    }
}

