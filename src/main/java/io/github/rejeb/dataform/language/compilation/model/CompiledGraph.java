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


import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
                .map(ca -> new CompiledQuery(ca.getTarget().getFullName(), ca.getQuery(),ca.isDisabled()))
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
                .map(ce -> new CompiledQuery(ce.getKey(), ce.getValue(),false))
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


    /**
     * The table compiled under a name, or, when none is, the table whose name before any table
     * prefix is that name.
     */
    public Optional<CompiledTable> findTableByName(String name) {
        ActionReference reference = ActionReference.named(name);
        return this.getTables().stream().filter(t -> reference.matches(t.getTarget())).findFirst()
                .or(() -> this.getTables().stream()
                        .filter(t -> reference.matches(t.getCanonicalTarget())).findFirst());
    }

    public Optional<CompiledAssertion> findAssertionByName(String name) {
        return this.getAssertions().stream().filter(t -> t.getTarget().getName().equals(name)).findFirst();
    }

    public Optional<CompiledOperation> findOperationByName(String name) {
        return this.getOperations().stream().filter(t -> t.getTarget().getName().equals(name)).findFirst();
    }

    /**
     * The declaration of a name, or, when none declares it, the one whose canonical name it is.
     */
    public Optional<Declaration> findDeclarationByName(String name) {
        ActionReference reference = ActionReference.named(name);
        return this.getDeclarations().stream().filter(d -> reference.matches(d.getTarget())).findFirst()
                .or(() -> this.getDeclarations().stream()
                        .filter(d -> reference.matches(d.getCanonicalTarget())).findFirst());
    }

    public Optional<Target> findTargetByRefName(String refName) {
        return findTargetByReference(ActionReference.named(refName));
    }

    /**
     * The target a {@code ref()} designates. Actions are first matched on the target they compile
     * to, then on their canonical target, which is what the project wrote before any table prefix
     * or schema suffix was applied.
     *
     * @param reference the name, schema and database the call gave
     * @return the target of the first action designated
     */
    public Optional<Target> findTargetByReference(ActionReference reference) {
        return targetsOfEveryAction()
                .filter(targets -> reference.matches(targets[0]))
                .map(targets -> targets[0])
                .findFirst()
                .or(() -> targetsOfEveryAction()
                        .filter(targets -> reference.matches(targets[1]))
                        .map(targets -> targets[0])
                        .findFirst());
    }

    private Stream<Target[]> targetsOfEveryAction() {
        return Stream.of(
                getTables().stream().map(t -> new Target[]{t.getTarget(), t.getCanonicalTarget()}),
                getDeclarations().stream().map(d -> new Target[]{d.getTarget(), d.getCanonicalTarget()}),
                getAssertions().stream().map(a -> new Target[]{a.getTarget(), null}),
                getOperations().stream().map(o -> new Target[]{o.getTarget(), null})
        ).flatMap(s -> s);
    }

    public List<String> getTags(String fileName) {
        return Stream.of(
                        findTableByFileName(fileName).stream()
                                .flatMap(t -> t.getTags().stream()),
                        findAssertionByFileName(fileName).stream()
                                .flatMap(a -> a.getTags().stream()),
                        findOperationByFileName(fileName).stream()
                                .flatMap(o -> o.getTags().stream())
                ).flatMap(s -> s)
                .distinct()
                .collect(Collectors.toList());
    }

    public Set<String> getTags() {
        return Stream.of(
                        getTables().stream()
                                .flatMap(t -> t.getTags().stream()),
                        getAssertions().stream()
                                .flatMap(a -> a.getTags().stream()),
                        getOperations().stream()
                                .flatMap(o -> o.getTags().stream())
                )
                .flatMap(s -> s)
                .collect(Collectors.toSet());
    }

    public List<String> getAllTargets() {
        return Stream.of(
                        getTables().stream()
                                .map(t -> t.getTarget().getFullName()),
                        getAssertions().stream()
                                .map(a -> a.getTarget().getFullName()),
                        getOperations().stream()
                                .map(o -> o.getTarget().getFullName())
                )
                .flatMap(s -> s)
                .distinct()
                .sorted()
                .toList();
    }

    @Nullable
    public String actionFileName(String actionName) {
        return findContainingFileInTable(actionName)
                .or(() -> findContainingFileInAssertion(actionName))
                .or(() -> findContainingFileInOperation(actionName))
                .orElse(null);
    }


    public Optional<String> findContainingFileInTable(String actionName) {
        return this.getTables()
                .stream()
                .filter(t -> t.getTarget().getFullName().equals(actionName))
                .findFirst()
                .map(CompiledTable::getFileName);
    }

    public Optional<String> findContainingFileInAssertion(String actionName) {
        return this.getAssertions()
                .stream()
                .filter(t -> t.getTarget().getFullName().equals(actionName))
                .findFirst()
                .map(CompiledAssertion::getFileName);
    }

    public Optional<String> findContainingFileInOperation(String actionName) {
        return this.getOperations()
                .stream()
                .filter(t -> t.getTarget().getFullName().equals(actionName))
                .findFirst()
                .map(CompiledOperation::getFileName);
    }
}

