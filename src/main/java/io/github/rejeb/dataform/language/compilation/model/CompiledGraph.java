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
import java.util.Optional;
import java.util.stream.Collectors;

public class CompiledGraph {
    private List<CompiledTable> tables;
    private List<CompiledAssertion> assertions;
    private List<CompiledOperation> operations;
    private List<Declaration> declarations;
    private ProjectConfig projectConfig;
    private GraphErrors graphErrors;

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

    public GraphErrors getGraphErrors() {
        return graphErrors;
    }

    public void setGraphErrors(GraphErrors graphErrors) {
        this.graphErrors = graphErrors;
    }

    public List<CompiledQuery> findCompiledQueryByFileName(String fileName) {
        List<CompiledQuery> tableQueries = findTableByFileName(fileName).stream().map(CompiledTable::getQueries).toList();

        List<CompiledQuery> assertionQueries = findAssertionByFileName(fileName)
                .stream()
                .map(ca -> new CompiledQuery(ca.getTarget().getFullName(), ca.getQuery()))
                .toList();

        List<CompiledQuery> operationQueries = findOperationByFileName(fileName)
                .stream()
                .map(CompiledOperation::getCompiledQueries)
                .toList();
        List<CompiledQuery> compilationError = findCompilationErrorByFileName(fileName)
                .stream()
                .collect(Collectors.groupingBy(ce -> ce.getActionName() != null ? ce.getActionName() : ce.getFileName(),
                        Collectors.mapping(CompilationError::getStack, Collectors.toList())

                ))
                .entrySet()
                .stream()
                .map(ce -> new CompiledQuery(ce.getKey(), ce.getValue()))
                .toList();
        List<CompiledQuery> queries = new ArrayList<>();
        queries.addAll(tableQueries);
        queries.addAll(assertionQueries);
        queries.addAll(operationQueries);
        queries.addAll(compilationError);

        return queries;
    }

    public List<CompiledTable> findTableByFileName(String fileName) {
        return this.getTables().stream().filter(t -> t.matchFileName(fileName)).toList();
    }

    public List<CompiledAssertion> findAssertionByFileName(String fileName) {
        return this.getAssertions().stream().filter(t -> t.matchFileName(fileName)).toList();
    }

    public List<CompiledOperation> findOperationByFileName(String fileName) {
        return this.getOperations().stream().filter(t -> t.matchFileName(fileName)).toList();
    }

    public List<Declaration> findDeclarationByFileName(String fileName) {
        return this.getDeclarations().stream().filter(t -> t.matchFileName(fileName)).toList();
    }


    public List<CompilationError> findCompilationErrorByFileName(String fileName) {
        return this.getGraphErrors().getCompilationErrors().stream().filter(t -> t.matchFileName(fileName)).toList();
    }


    public Optional<CompiledTable> findTableByName(String name) {
        return this.getTables().stream().filter(t -> t.getTarget().getName().equals(name)).findFirst();
    }

    public Optional<CompiledAssertion> findAssertionByName(String name) {
        return this.getAssertions().stream().filter(t -> t.getTarget().getName().equals(name)).findFirst();
    }

    public Optional<CompiledOperation> findOperationByName(String name) {
        return this.getOperations().stream().filter(t -> t.getTarget().getName().equals(name)).findFirst();
    }

    public Optional<Declaration> findDeclarationByName(String name) {
        return this.getDeclarations().stream().filter(t -> t.getTarget().getName().equals(name)).findFirst();
    }

    public Optional<Target> findTargetByRefName(String refName) {
        return findTableByName(refName).map(CompiledTable::getTarget)
                .or(() -> findDeclarationByName(refName).map(Declaration::getTarget))
                .or(() -> findAssertionByName(refName).map(CompiledAssertion::getTarget))
                .or(() -> findOperationByName(refName).map(CompiledOperation::getTarget));
    }

}

