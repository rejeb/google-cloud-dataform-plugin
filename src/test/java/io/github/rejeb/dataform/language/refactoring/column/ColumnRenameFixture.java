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
package io.github.rejeb.dataform.language.refactoring.column;

import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.compilation.DataformCompilationService;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.compilation.model.CompiledTable;
import io.github.rejeb.dataform.language.compilation.model.Target;
import io.github.rejeb.dataform.language.schema.sql.DataformTableSchemaService;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A Dataform project built for one test: the actions it declares, what each reads, and the schema
 * the last compilation published for them.
 *
 * <p>The shared fixture of the goto and usages tests describes one fixed project; a rename has to be
 * tried against shapes that project does not have, a star reading an action of its own above all.</p>
 */
public abstract class ColumnRenameFixture extends BasePlatformTestCase {

    /**
     * One action of the project.
     *
     * @param name         the action name, which is also its file name
     * @param query        the SQL the compilation produced for it
     * @param dependencies the actions it reads
     * @param columns      the columns its schema declares
     */
    public record Action(String name, String query, List<String> dependencies, List<String> columns) {
    }

    private final Map<String, PsiFile> files = new LinkedHashMap<>();

    /** Installs the compiled graph and the schema of a project made of the given actions. */
    protected void installProject(Action... actions) {
        List<CompiledTable> tables = new ArrayList<>();
        StringBuilder schema = new StringBuilder("{");
        for (Action action : actions) {
            tables.add(tableOf(action));
            if (schema.length() > 1) schema.append(",");
            schema.append(schemaEntry(action));
        }
        schema.append("}");

        CompiledGraph graph = new CompiledGraph();
        set(graph, "tables", tables);
        set(graph, "declarations", List.of());
        set(graph, "operations", List.of());
        set(graph, "assertions", List.of());
        set(getProject().getService(DataformCompilationService.class), "compiledGraph", graph);

        DataformTableSchemaService.State state = new DataformTableSchemaService.State();
        state.schemaCacheJson = schema.toString();
        DataformTableSchemaService.getInstance(getProject()).loadState(state);
    }

    /** Adds the SQLX file of an action to the project and opens it. */
    protected PsiFile addFile(String action, String text) {
        PsiFile file = myFixture.addFileToProject("definitions/" + action + ".sqlx", text);
        files.put(action, file);
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        return myFixture.getFile();
    }

    /** The file of an action, as it stands now. */
    protected PsiFile fileOf(String action) {
        return files.get(action);
    }

    private CompiledTable tableOf(Action action) {
        CompiledTable table = new CompiledTable();
        set(table, "type", "table");
        set(table, "target", targetOf(action.name()));
        set(table, "query", action.query());
        set(table, "fileName", "definitions/" + action.name() + ".sqlx");
        set(table, "tags", List.of());
        set(table, "dependencyTargets", action.dependencies().stream().map(this::targetOf).toList());
        return table;
    }

    private Target targetOf(String name) {
        Target target = new Target();
        set(target, "database", "p");
        set(target, "schema", "d");
        set(target, "name", name);
        return target;
    }

    private String schemaEntry(Action action) {
        StringBuilder columns = new StringBuilder();
        for (String column : action.columns()) {
            if (!columns.isEmpty()) columns.append(",");
            columns.append("{\"name\":\"").append(column)
                    .append("\",\"type\":\"STRING\",\"mode\":\"NULLABLE\",\"subFields\":[]}");
        }
        return "\"p.d." + action.name() + "\":{\"columns\":[" + columns
                + "],\"lastModified\":0,\"fileName\":\"definitions/" + action.name() + ".sqlx\"}";
    }

    private static void set(Object target, String field, Object value) {
        try {
            Field declared = target.getClass().getDeclaredField(field);
            declared.setAccessible(true);
            declared.set(target, value);
        } catch (Exception e) {
            throw new IllegalStateException("set " + field, e);
        }
    }
}
