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
package io.github.rejeb.dataform.language.completion;

import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.compilation.model.Target;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public final class DataformActionLookups {

    public static final double ACTION_PRIORITY = 200.0;
    public static final double SCHEMA_PRIORITY = 300.0;
    public static final double PROJECT_PRIORITY = 400.0;

    private DataformActionLookups() {
    }

    /**
     * Returns a lookup per enabled table, view and incremental table of the graph.
     */
    @NotNull
    public static List<LookupElement> tables(@NotNull CompiledGraph graph) {
        return tablesIn(graph, null);
    }

    /**
     * Returns a lookup per declared source of the graph.
     */
    @NotNull
    public static List<LookupElement> declarations(@NotNull CompiledGraph graph) {
        return declarationsIn(graph, null);
    }

    /**
     * Returns a lookup per enabled table, view and incremental table of the graph living in the given
     * schema, as {@code ref()} names it.
     *
     * @param schema the schema, {@code null} for every schema
     */
    @NotNull
    public static List<LookupElement> tablesIn(@NotNull CompiledGraph graph, @Nullable String schema) {
        return graph.getTables().stream()
                .filter(table -> table.getTarget() != null && !table.isDisabled())
                .filter(table -> schema == null || schema.equals(refSchema(table.getCanonicalTarget(), table.getTarget())))
                .map(table -> lookup(table.getTarget(), AllIcons.Nodes.DataTables, table.getType(), ACTION_PRIORITY))
                .toList();
    }

    /**
     * Returns a lookup per declared source of the graph living in the given schema, as {@code ref()}
     * names it.
     *
     * @param schema the schema, {@code null} for every schema
     */
    @NotNull
    public static List<LookupElement> declarationsIn(@NotNull CompiledGraph graph, @Nullable String schema) {
        return graph.getDeclarations().stream()
                .filter(declaration -> declaration.getTarget() != null)
                .filter(declaration -> schema == null
                        || schema.equals(refSchema(declaration.getCanonicalTarget(), declaration.getTarget())))
                .map(declaration -> lookup(declaration.getTarget(), AllIcons.Nodes.DataSchema, "source", ACTION_PRIORITY))
                .toList();
    }

    /**
     * Returns a lookup per distinct schema of the tables and declarations of the graph, as
     * {@code ref()} names it: before any schema suffix the compilation applies.
     */
    @NotNull
    public static List<LookupElement> schemas(@NotNull CompiledGraph graph) {
        Set<String> schemas = new TreeSet<>();
        graph.getTables().forEach(table -> addSchema(schemas, refSchema(table.getCanonicalTarget(), table.getTarget())));
        graph.getDeclarations().forEach(declaration ->
                addSchema(schemas, refSchema(declaration.getCanonicalTarget(), declaration.getTarget())));
        return schemas.stream()
                .map(schema -> PrioritizedLookupElement.withPriority(LookupElementBuilder.create(schema)
                        .withIcon(AllIcons.Nodes.Folder)
                        .withTypeText("schema"), SCHEMA_PRIORITY))
                .toList();
    }

    /**
     * Returns a lookup completing the given Google Cloud project, above schemas and tables.
     */
    @NotNull
    public static LookupElement project(@NotNull String project) {
        return PrioritizedLookupElement.withPriority(LookupElementBuilder.create(project)
                .withIcon(AllIcons.Nodes.Module)
                .withTypeText("project"), PROJECT_PRIORITY);
    }

    /**
     * Tells whether the given name is the database of a table or declaration of the graph.
     */
    public static boolean isDatabase(@NotNull CompiledGraph graph, @NotNull String name) {
        return graph.getTables().stream()
                .anyMatch(table -> hasDatabase(table.getTarget(), name) || hasDatabase(table.getCanonicalTarget(), name))
                || graph.getDeclarations().stream()
                .anyMatch(declaration -> hasDatabase(declaration.getTarget(), name)
                        || hasDatabase(declaration.getCanonicalTarget(), name));
    }

    private static boolean hasDatabase(@Nullable Target target, @NotNull String name) {
        return target != null && name.equals(target.getDatabase());
    }

    @Nullable
    private static String refSchema(@Nullable Target canonical, @Nullable Target compiled) {
        if (canonical != null && canonical.getSchema() != null) {
            return canonical.getSchema();
        }
        return compiled == null ? null : compiled.getSchema();
    }

    private static void addSchema(@NotNull Set<String> schemas, @Nullable String schema) {
        if (schema != null && !schema.isBlank()) {
            schemas.add(schema);
        }
    }

    /**
     * Returns a lookup completing the name of the given target, showing its full name as tail text.
     */
    @NotNull
    public static LookupElement lookup(@NotNull Target target,
                                       @NotNull Icon icon,
                                       @Nullable String typeText,
                                       double priority) {
        LookupElementBuilder element = LookupElementBuilder.create(target.getName())
                .withIcon(icon)
                .withTypeText(typeText)
                .withTailText(" (" + target.getFullName() + ")", true);
        return PrioritizedLookupElement.withPriority(element, priority);
    }
}
