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
package io.github.rejeb.dataform.language.service;

import com.intellij.lang.javascript.psi.JSFunction;
import com.intellij.lang.javascript.psi.JSVariable;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Optional;

/**
 * Symbols exported by the {@code @dataform/core} package installed for the project: the builtin
 * functions such as {@code ref()} or {@code publish()} and the builtin variables.
 */
public interface DataformCoreIndexService {

    static DataformCoreIndexService getInstance(Project project) {
        return project.getService(DataformCoreIndexService.class);
    }

    /**
     * @return the {@code bundle.d.ts} of the core package, when the package is installed
     */
    Optional<PsiFile> getPsiFile();

    /**
     * @return the builtin functions declared at the top level of the core declaration file
     */
    @NotNull
    Collection<JSFunction> getCachedDataformFunctionsRef();

    /**
     * @return the builtin variables declared at the top level of the core declaration file
     */
    @NotNull
    Collection<JSVariable> getCachedDataformVariablesRef();

    /**
     * @return the builtin functions shaped for code completion
     */
    @NotNull
    Collection<DataformFunctionCompletionObject> getCachedDataformFunctionsForCompletion();
}
