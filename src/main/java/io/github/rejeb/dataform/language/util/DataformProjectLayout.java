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
package io.github.rejeb.dataform.language.util;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * Locates the well-known files and directories of a Dataform project relative to a file, walking
 * the virtual file system only. All methods are index-free and safe on any thread, including the
 * EDT, and support Dataform projects nested inside a larger repository.
 */
public final class DataformProjectLayout {

    public static final String WORKFLOW_SETTINGS_YAML = "workflow_settings.yaml";
    public static final String DATAFORM_JSON = "dataform.json";
    public static final String INCLUDES_DIR = "includes";
    public static final String DEFINITIONS_DIR = "definitions";

    private static final String JS_EXTENSION = "js";

    private DataformProjectLayout() {
    }

    /**
     * Returns the {@code workflow_settings.yaml} of the Dataform project the given file belongs to,
     * found by walking up the directory tree, or {@code null} when there is none.
     */
    @Nullable
    public static VirtualFile findWorkflowSettings(@Nullable VirtualFile context) {
        VirtualFile directory = directoryOf(context);
        while (directory != null) {
            VirtualFile candidate = directory.findChild(WORKFLOW_SETTINGS_YAML);
            if (candidate != null && !candidate.isDirectory()) {
                return candidate;
            }
            directory = directory.getParent();
        }
        return null;
    }

    /**
     * Returns the global names of the {@code includes/*.js} files of the nearest {@code includes}
     * directory above the given file, or an empty set when there is none.
     */
    @NotNull
    public static Set<String> includeNames(@Nullable VirtualFile context) {
        VirtualFile directory = directoryOf(context);
        while (directory != null) {
            VirtualFile includes = directory.findChild(INCLUDES_DIR);
            if (includes != null && includes.isDirectory()) {
                Set<String> names = new HashSet<>();
                for (VirtualFile child : includes.getChildren()) {
                    if (!child.isDirectory() && JS_EXTENSION.equals(child.getExtension())) {
                        names.add(child.getNameWithoutExtension());
                    }
                }
                return names;
            }
            directory = directory.getParent();
        }
        return Set.of();
    }

    /**
     * Tells whether the given file plausibly belongs to a Dataform project: under a
     * {@code definitions} or {@code includes} directory, or below a directory holding
     * {@code workflow_settings.yaml} or {@code dataform.json}. Cheap enough for hot-path early
     * exits.
     */
    public static boolean isInDataformProject(@Nullable VirtualFile file) {
        if (file == null) {
            return false;
        }
        String path = file.getPath().replace('\\', '/');
        if (path.contains("/" + DEFINITIONS_DIR + "/") || path.contains("/" + INCLUDES_DIR + "/")) {
            return true;
        }
        VirtualFile directory = directoryOf(file);
        while (directory != null) {
            if (directory.findChild(WORKFLOW_SETTINGS_YAML) != null
                    || directory.findChild(DATAFORM_JSON) != null) {
                return true;
            }
            directory = directory.getParent();
        }
        return false;
    }

    @Nullable
    private static VirtualFile directoryOf(@Nullable VirtualFile file) {
        return file == null || file.isDirectory() ? file : file.getParent();
    }
}
