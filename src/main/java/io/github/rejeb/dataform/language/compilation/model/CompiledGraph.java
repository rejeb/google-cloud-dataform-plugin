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
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
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
        return matching(fileNameIndex().tables(), fileName);
    }

    public List<CompiledAssertion> findAssertionByFileName(String fileName) {
        return matching(fileNameIndex().assertions(), fileName);
    }

    public List<CompiledOperation> findOperationByFileName(String fileName) {
        return matching(fileNameIndex().operations(), fileName);
    }

    public List<Declaration> findDeclarationByFileName(String fileName) {
        return matching(fileNameIndex().declarations(), fileName);
    }

    /**
     * The actions compiled from a file, tables first, then operations, assertions and declarations.
     *
     * @param fileName the path of the file, matched whatever separator the compiler wrote
     * @return the actions of the file that have a target
     */
    public @NotNull List<GraphAction> actionsOfFile(@NotNull String fileName) {
        List<GraphAction> actions = new ArrayList<>();
        findTableByFileName(fileName).forEach(t -> addAction(actions, t.getTarget(), t.getType(), t.getFileName(),
                t.getDependencyTargets(), true));
        findOperationByFileName(fileName).forEach(o -> addAction(actions, o.getTarget(), "operation", o.getFileName(),
                o.getDependencyTargets(), o.isHasOutput()));
        findAssertionByFileName(fileName).forEach(a -> addAction(actions, a.getTarget(), "assertion", a.getFileName(),
                a.getDependencyTargets(), false));
        findDeclarationByFileName(fileName).forEach(d -> addAction(actions, d.getTarget(), "declaration",
                d.getFileName(), List.of(), false));
        return actions;
    }

    /**
     * The file of the action building a table from a query: a table, or an operation with output.
     *
     * @param tableFullName the full name of the table
     * @return the project-relative file name, empty when no action of the project builds the table
     */
    public @NotNull Optional<String> fileBuilding(@NotNull String tableFullName) {
        return getTables().stream()
                .filter(t -> t.getTarget() != null && tableFullName.equals(t.getTarget().getFullName()))
                .map(CompiledTable::getFileName)
                .findFirst()
                .or(() -> getOperations().stream()
                        .filter(o -> o.isHasOutput() && o.getTarget() != null
                                && tableFullName.equals(o.getTarget().getFullName()))
                        .map(CompiledOperation::getFileName)
                        .findFirst());
    }

    private static void addAction(@NotNull List<GraphAction> actions, @Nullable Target target, @NotNull String kind,
                                  @Nullable String fileName, @Nullable List<Target> dependencies, boolean buildsTable) {
        if (target != null) {
            actions.add(new GraphAction(target, kind, fileName, dependencies != null ? dependencies : List.of(),
                    buildsTable));
        }
    }

    public List<CompilationError> findCompilationErrorByFileName(String fileName) {
        if (graphErrors == null || graphErrors.getCompilationErrors() == null) return List.of();
        return graphErrors.getCompilationErrors().stream().filter(t -> t.matchFileName(fileName)).toList();
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

    /**
     * The assertion a reference designates, matched on the target it compiles to.
     *
     * @param reference the name, schema and database to match
     * @return the first assertion designated
     */
    public Optional<CompiledAssertion> findAssertionByReference(ActionReference reference) {
        return this.getAssertions().stream().filter(a -> reference.matches(a.getTarget())).findFirst();
    }

    /**
     * The operation a reference designates, matched on the target it compiles to.
     *
     * @param reference the name, schema and database to match
     * @return the first operation designated
     */
    public Optional<CompiledOperation> findOperationByReference(ActionReference reference) {
        return this.getOperations().stream().filter(o -> reference.matches(o.getTarget())).findFirst();
    }

    /**
     * The declaration a reference designates, matched on the target it compiles to, then on its
     * canonical target.
     *
     * @param reference the name, schema and database to match
     * @return the first declaration designated
     */
    public Optional<Declaration> findDeclarationByReference(ActionReference reference) {
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
        Optional<Target> compiled = Stream.of(getTables(), getDeclarations(), getAssertions(), getOperations())
                .<CompiledAction>flatMap(List::stream)
                .map(CompiledAction::getTarget)
                .filter(reference::matches)
                .findFirst();
        if (compiled.isPresent()) return compiled;
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

    /**
     * The file of the action a reference designates, matched as {@link #findTargetByReference} does.
     *
     * @param reference the name, schema and database to match
     * @return the project-relative file name, empty when no action carries it
     */
    public @NotNull Optional<String> fileOf(@NotNull ActionReference reference) {
        return findTargetByReference(reference).flatMap(target -> Stream.of(
                        getTables(), getDeclarations(), getOperations(), getAssertions())
                .<CompiledAction>flatMap(List::stream)
                .filter(action -> target.equals(action.getTarget()))
                .map(CompiledAction::getFileName)
                .filter(Objects::nonNull)
                .findFirst());
    }

    public List<String> getTags(String fileName) {
        return Stream.of(findTableByFileName(fileName), findAssertionByFileName(fileName),
                        findOperationByFileName(fileName))
                .<ExecutableAction>flatMap(List::stream)
                .flatMap(action -> action.getTags().stream())
                .distinct()
                .collect(Collectors.toList());
    }

    public Set<String> getTags() {
        return executableActions()
                .flatMap(action -> action.getTags().stream())
                .collect(Collectors.toSet());
    }

    public List<String> getAllTargets() {
        return executableActions()
                .map(action -> action.getTarget().getFullName())
                .distinct()
                .sorted()
                .toList();
    }

    @Nullable
    public String actionFileName(String actionName) {
        return executableActions()
                .filter(action -> action.getTarget().getFullName().equals(actionName))
                .findFirst()
                .map(CompiledAction::getFileName)
                .orElse(null);
    }

    private @NotNull Stream<ExecutableAction> executableActions() {
        return Stream.of(getTables(), getAssertions(), getOperations()).<ExecutableAction>flatMap(List::stream);
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
                    byLastSegment(getTables()),
                    byLastSegment(getAssertions()),
                    byLastSegment(getOperations()),
                    byLastSegment(getDeclarations()));
            fileNameIndex = index;
        }
        return index;
    }

    private static <T extends CompiledAction> @NotNull Map<String, List<T>> byLastSegment(@NotNull List<T> actions) {
        Map<String, List<T>> index = new HashMap<>();
        for (T action : actions) {
            String fileName = action.getFileName();
            if (fileName == null) continue;
            index.computeIfAbsent(lastSegment(fileName), key -> new ArrayList<>(1)).add(action);
        }
        return index;
    }

    private static <T extends CompiledAction> @NotNull List<T> matching(@NotNull Map<String, List<T>> index,
                                                                        @Nullable String fileName) {
        if (fileName == null) return List.of();
        List<T> candidates = index.get(lastSegment(DataformPaths.normalize(fileName)));
        if (candidates == null) return List.of();
        List<T> result = new ArrayList<>(candidates.size());
        for (T candidate : candidates) {
            if (candidate.matchFileName(fileName)) result.add(candidate);
        }
        return Collections.unmodifiableList(result);
    }

    private static @NotNull String lastSegment(@NotNull String normalizedPath) {
        return normalizedPath.substring(normalizedPath.lastIndexOf('/') + 1);
    }
}
