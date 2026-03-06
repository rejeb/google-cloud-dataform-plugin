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
package io.github.rejeb.dataform.language.fileEditor;

import io.github.rejeb.dataform.language.compilation.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class LineageGraphHelper {

    public static List<LineageGraph> buildGraph(CompiledGraph graph, String fileName) {
        List<LineageGraph> compiledTable = graph.findTableByFileName(fileName)
                .stream()
                .map(t -> buildLineageGraph(graph, t)).toList();

        List<LineageGraph> compiledAssertion = graph.findAssertionByFileName(fileName)
                .stream()
                .map(t -> buildLineageGraph(graph, t)).toList();

        List<LineageGraph> declarations = graph.findDeclarationByFileName(fileName)
                .stream()
                .map(t -> buildLineageGraph(graph, t)).toList();

        List<LineageGraph> operations = graph.findOperationByFileName(fileName)
                .stream()
                .map(t -> buildLineageGraph(graph, t)).toList();
        List<LineageGraph> allDependencies = new ArrayList<>();
        allDependencies.addAll(compiledTable);
        allDependencies.addAll(compiledAssertion);
        allDependencies.addAll(declarations);
        allDependencies.addAll(operations);

        return allDependencies;
    }


    private static LineageGraph buildLineageGraph(CompiledGraph graph, CompiledTable table) {
        return new LineageGraph(
                buildGraphTarget(table),
                table.getDependencyTargets().stream().map(t -> buildGraphTarget(graph, t.getName())).toList(),
                findDependentTarget(graph, table.getTarget().getName())
        );
    }

    private static LineageGraph buildLineageGraph(CompiledGraph graph, CompiledAssertion table) {
        return new LineageGraph(
                buildGraphTarget(table),
                table.getDependencyTargets().stream().map(t -> buildGraphTarget(graph, t.getName())).toList(),
                findDependentTarget(graph, table.getTarget().getName())
        );
    }

    private static LineageGraph buildLineageGraph(CompiledGraph graph, Declaration table) {
        return new LineageGraph(
                buildGraphTarget(table),
                new ArrayList<>(),
                findDependentTarget(graph, table.getTarget().getName())
        );
    }

    private static LineageGraph buildLineageGraph(CompiledGraph graph, CompiledOperation table) {
        return new LineageGraph(
                buildGraphTarget(table),
                new ArrayList<>(),
                findDependentTarget(graph, table.getTarget().getName())
        );
    }

    private static List<GraphTarget> findDependentTarget(CompiledGraph graph, String name) {
        List<GraphTarget> tableTargets = graph.getTables()
                .stream()
                .filter(t -> t.getDependencyTargets().stream().anyMatch(d -> d.getName().equals(name)))
                .map(LineageGraphHelper::buildGraphTarget).toList();
        List<GraphTarget> assertionTargets = graph.getAssertions()
                .stream()
                .filter(t -> t.getDependencyTargets().stream().anyMatch(d -> d.getName().equals(name)))
                .map(LineageGraphHelper::buildGraphTarget).toList();
        List<GraphTarget> allDependent = new ArrayList<>();
        allDependent.addAll(tableTargets);
        allDependent.addAll(assertionTargets);
        return allDependent;
    }

    private static GraphTarget buildGraphTarget(CompiledTable table) {
        return new GraphTarget(
                table.getTarget().getName(),
                table.getTarget().getFullName(),
                table.getFileName(),
                table.getType());
    }

    private static GraphTarget buildGraphTarget(CompiledAssertion table) {
        return new GraphTarget(
                table.getTarget().getName(),
                table.getTarget().getFullName(),
                table.getFileName(),
                "assertion");
    }

    private static GraphTarget buildGraphTarget(Declaration declaration) {
        return new GraphTarget(
                declaration.getTarget().getName(),
                declaration.getTarget().getFullName(),
                declaration.getFileName(),
                "declaration");
    }

    private static GraphTarget buildGraphTarget(CompiledOperation operation) {
        return new GraphTarget(
                operation.getTarget().getName(),
                operation.getTarget().getFullName(),
                operation.getFileName(),
                "operation");
    }

    private static GraphTarget buildGraphTarget(CompiledGraph graph, String name) {
        Optional<GraphTarget> tableTarget = graph.findTableByName(name).map(LineageGraphHelper::buildGraphTarget);
        if (tableTarget.isPresent()) {
            return tableTarget.get();
        }

        Optional<GraphTarget> declarationTarget = graph.findDeclarationByName(name).map(LineageGraphHelper::buildGraphTarget);
        if (declarationTarget.isPresent()) {
            return declarationTarget.get();
        }

        Optional<GraphTarget> assertionTarget = graph.findAssertionByName(name).map(LineageGraphHelper::buildGraphTarget);
        if (assertionTarget.isPresent()) {
            return assertionTarget.get();
        }

        Optional<GraphTarget> operationTarget = graph.findOperationByName(name).map(LineageGraphHelper::buildGraphTarget);
        if (operationTarget.isPresent()) {
            return operationTarget.get();
        }

        return new GraphTarget(name, null, null,null);
    }
}
