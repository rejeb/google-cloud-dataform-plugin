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
package io.github.rejeb.dataform.language.schema.sql;

import com.intellij.openapi.diagnostic.Logger;
import io.github.rejeb.dataform.language.compilation.model.SortableAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Reads the source file an action was compiled from. Both the planner, which decides what changed,
 * and the extraction, which records what it just read, need it.
 */
final class ActionSourceFiles {

    private static final Logger LOG = Logger.getInstance(ActionSourceFiles.class);

    private ActionSourceFiles() {
    }

    /** The project-relative source file of an action, or {@code null} when it declares none. */
    @Nullable
    static String fileNameOf(@NotNull SortableAction action) {
        if (action.isTable()) return action.table().getFileName();
        if (action.isOperation()) return action.operation().getFileName();
        if (action.isDeclaration()) return action.declaration().getFileName();
        return null;
    }

    /**
     * Last modification time of an action source file, or {@code 0} when it cannot be read. A
     * missing file must not report the current time: that would mark the action modified on every
     * pass and re-extract it forever.
     */
    static long modificationTime(@NotNull String basePath, @NotNull String fileName) {
        try {
            Path filePath = Paths.get(basePath, fileName);
            if (Files.exists(filePath)) return Files.getLastModifiedTime(filePath).toMillis();
        } catch (Exception e) {
            LOG.debug("Failed to get modification time for " + fileName + ": " + e.getMessage());
        }
        return 0L;
    }
}
