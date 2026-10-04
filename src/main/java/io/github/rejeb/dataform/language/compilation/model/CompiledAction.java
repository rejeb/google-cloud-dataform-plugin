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

/**
 * An action of the compiled graph: something compiled from a file of the project to a target.
 */
public interface CompiledAction {

    /**
     * Returns the target the action compiles to.
     */
    Target getTarget();

    /**
     * Returns the project-relative path of the file the action is compiled from, with {@code /}
     * separators.
     */
    String getFileName();

    /**
     * Whether a path designates the file the action is compiled from, whatever separator either
     * path uses.
     *
     * @param fileName the path to test
     * @return whether it points to the file of the action
     */
    default boolean matchFileName(String fileName) {
        return DataformPaths.pointsTo(fileName, getFileName());
    }
}
