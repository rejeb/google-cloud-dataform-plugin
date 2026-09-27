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

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CompiledGraph {
    private List<CompiledTable> tables;
    private List<CompiledAssertion> assertions;
    private List<CompiledOperation> operations;
    private List<Declaration> declarations;
    private List<CompiledTest> tests;
    private ProjectConfig projectConfig;
    private GraphErrors graphErrors;
    private transient volatile FileNameIndex fileNameIndex;

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

    public List<CompiledTest> getTests() {
        return tests != null ? tests : Collections.emptyList();
    }

    public List<CompiledTest> findTestByFileName(String fileName) {
        return getTests().stream().filter(test -> test.matchFileName(fileName)).toList();
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
        return matching(fileNameIndex().tables(), fileName, CompiledTable::matchFileName);
    }

    public List<CompiledAssertion> findAssertionByFileName(String fileName) {
        return matching(fileNameIndex().assertions(), fileName, CompiledAssertion::matchFileName);
    }

    public List<CompiledOperation> findOperationByFileName(String fileName) {
        return matching(fileNameIndex().operations(), fileName, CompiledOperation::matchFileName);
    }

    public List<Declaration> findDeclarationByFileName(String fileName) {
        return matching(fileNameIndex().declarations(), fileName, Declaration::matchFileName);
    }


    public List<CompilationError> findCompilationErrorByFileName(String fileName) {
        return this.getGraphErrors().getCompilationErrors().stream().filter(t -> t.matchFileName(fileName)).toList();
    }


    /**
     * The table compiled under a name, or, when none is, the table whose name before any table
     * prefix is that name.
     */
    public Optional<CompiledTable> findTableByName(String name) {
        return findTableByReference(ActionReference.named(name));
    }

    /**
     * The table a reference designates, matched on the target it compiles to, then on its
     * canonical target.
     *
     * @param reference the name, schema and database to match
     * @return the first table designated
     */
    public Optional<CompiledTable> findTableByReference(ActionReference reference) {
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
        for (CompiledTable table : getTables()) {
            if (reference.matches(table.getTarget())) return Optional.of(table.getTarget());
        }
        for (Declaration declaration : getDeclarations()) {
            if (reference.matches(declaration.getTarget())) return Optional.of(declaration.getTarget());
        }
        for (CompiledAssertion assertion : getAssertions()) {
            if (reference.matches(assertion.getTarget())) return Optional.of(assertion.getTarget());
        }
        for (CompiledOperation operation : getOperations()) {
            if (reference.matches(operation.getTarget())) return Optional.of(operation.getTarget());
        }
        for (CompiledTable table : getTables()) {
            if (reference.matches(table.getCanonicalTarget())) return Optional.ofNullable(table.getTarget());
        }
        for (Declaration declaration : getDeclarations()) {
            if (reference.matches(declaration.getCanonicalTarget())) {
                return Optional.ofNullable(declaration.getTarget());
            }
        }
        return Optional.empty();
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

    @FunctionalInterface
    private interface FileNameMatcher<T> {
        boolean matches(T action, String fileName);
    }

    /**
     * The actions of each kind grouped by the last segment of their file name. A path can only
     * designate an action whose file name ends like it, so a lookup tests the few actions sharing
     * that segment instead of every action of the graph.
     */
    private record FileNameIndex(@NotNull List<?>[] sources,
                                 int @NotNull [] sizes,
                                 @NotNull Map<String, List<CompiledTable>> tables,
                                 @NotNull Map<String, List<CompiledAssertion>> assertions,
                                 @NotNull Map<String, List<CompiledOperation>> operations,
                                 @NotNull Map<String, List<Declaration>> declarations) {

        boolean isFor(@NotNull List<?> @NotNull [] lists) {
            for (int i = 0; i < lists.length; i++) {
                if (sources[i] != lists[i] || sizes[i] != lists[i].size()) return false;
            }
            return true;
        }
    }

    private @NotNull FileNameIndex fileNameIndex() {
        List<?>[] lists = {getTables(), getAssertions(), getOperations(), getDeclarations()};
        FileNameIndex index = fileNameIndex;
        if (index == null || !index.isFor(lists)) {
            int[] sizes = new int[lists.length];
            for (int i = 0; i < lists.length; i++) sizes[i] = lists[i].size();
            index = new FileNameIndex(lists, sizes,
                    byLastSegment(getTables(), CompiledTable::getFileName),
                    byLastSegment(getAssertions(), CompiledAssertion::getFileName),
                    byLastSegment(getOperations(), CompiledOperation::getFileName),
                    byLastSegment(getDeclarations(), Declaration::getFileName));
            fileNameIndex = index;
        }
        return index;
    }

    private static <T> @NotNull Map<String, List<T>> byLastSegment(@NotNull List<T> actions,
                                                                   @NotNull Function<T, String> fileNameOf) {
        Map<String, List<T>> index = new HashMap<>();
        for (T action : actions) {
            String fileName = fileNameOf.apply(action);
            if (fileName == null) continue;
            index.computeIfAbsent(lastSegment(fileName), key -> new ArrayList<>(1)).add(action);
        }
        return index;
    }

    private static <T> @NotNull List<T> matching(@NotNull Map<String, List<T>> index,
                                                 @Nullable String fileName,
                                                 @NotNull FileNameMatcher<T> matcher) {
        if (fileName == null) return List.of();
        List<T> candidates = index.get(lastSegment(DataformPaths.normalize(fileName)));
        if (candidates == null) return List.of();
        List<T> result = new ArrayList<>(candidates.size());
        for (T candidate : candidates) {
            if (matcher.matches(candidate, fileName)) result.add(candidate);
        }
        return Collections.unmodifiableList(result);
    }

    private static @NotNull String lastSegment(@NotNull String normalizedPath) {
        return normalizedPath.substring(normalizedPath.lastIndexOf('/') + 1);
    }
}
