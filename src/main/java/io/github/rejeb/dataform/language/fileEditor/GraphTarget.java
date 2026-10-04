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
package io.github.rejeb.dataform.language.fileEditor;

import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.compilation.model.Target;

import java.util.List;

public record GraphTarget(String name, String fullName, String type) {

    /**
     * The actions a file compiles to, tables first, then operations, assertions and declarations.
     *
     * @param graph    the compiled graph
     * @param fileName the compiled file name of the file
     * @return one target per action of the file
     */
    public static List<GraphTarget> targetsOf(CompiledGraph graph, String fileName) {
        return graph.actionsOfFile(fileName).stream().map(action -> of(action.target(), action.kind())).toList();
    }

    private static GraphTarget of(Target target, String type) {
        return new GraphTarget(target.getName(), target.getFullName(), type);
    }
}
