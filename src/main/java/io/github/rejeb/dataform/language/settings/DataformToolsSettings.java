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
package io.github.rejeb.dataform.language.settings;

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;

public interface DataformToolsSettings {

    /**
     * Returns the singleton application-level instance.
     */
    static DataformToolsSettings getInstance() {
        return ApplicationManager.getApplication().getService(DataformToolsSettings.class);
    }

    /**
     * Returns the configured path to the Dataform Core install directory.
     */
    @NotNull String getCoreInstallPath();

    /**
     * Updates the Dataform Core install directory alone, leaving the SQLFluff settings untouched.
     */
    void setCoreInstallPath(@NotNull String coreInstallPath);

    /**
     * Updates all settings at once.
     */
    void update(
            @NotNull String coreInstallPath,
            @NotNull String sqlfluffExecutablePath,
            @NotNull String sqlfluffConfigPath,
            @NotNull String sqlfluffExtraArgs);

    /**
     * Returns the configured path to the SQLFluff executable.
     */
    @NotNull String getSqlfluffExecutablePath();

    /**
     * Returns the configured path to the SQLFluff config file.
     */
    @NotNull String getSqlfluffConfigPath();

    /**
     * Returns additional SQLFluff CLI arguments.
     */
    @NotNull String getSqlfluffExtraArgs();

    /**
     * Tells whether Dataform expressions are folded to their evaluated value in the editor.
     */
    boolean isFoldTemplateExpressions();

    /**
     * Enables or disables folding Dataform expressions to their evaluated value.
     */
    void setFoldTemplateExpressions(boolean value);

    /**
     * Tells whether compilation errors are shown inline in the editor.
     */
    boolean isShowInlineCompilationErrors();

    /**
     * Enables or disables showing compilation errors inline in the editor.
     */
    void setShowInlineCompilationErrors(boolean value);

    /**
     * Tells whether the project is recompiled in the background when a source file is saved.
     */
    boolean isCompileOnSave();

    /**
     * Enables or disables recompiling the project when a source file is saved.
     */
    void setCompileOnSave(boolean value);
}
