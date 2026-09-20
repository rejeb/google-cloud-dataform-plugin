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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Reaches the open projects from application-level listeners, which are notified once for the
 * whole IDE and have to dispatch the event themselves.
 */
public final class DataformProjects {

    private DataformProjects() {
    }

    /**
     * Runs the action on every open project that is still alive.
     */
    public static void forEachOpen(@NotNull Consumer<Project> action) {
        for (Project project : ProjectManager.getInstance().getOpenProjects()) {
            if (!project.isDisposed()) {
                action.accept(project);
            }
        }
    }

    /**
     * Runs the action on the open projects whose content holds the file. An application listener
     * hears of every project at once, and compiling or re-highlighting one because a file of
     * another changed costs a full Dataform CLI run for nothing. Needs read access.
     */
    public static void forEachOwning(@Nullable VirtualFile file, @NotNull Consumer<Project> action) {
        for (Project project : owning(file)) {
            action.accept(project);
        }
    }

    /**
     * The open projects whose content holds the file. Needs read access.
     */
    @NotNull
    public static List<Project> owning(@Nullable VirtualFile file) {
        List<Project> result = new ArrayList<>();
        if (file == null) {
            return result;
        }
        for (Project project : ProjectManager.getInstance().getOpenProjects()) {
            if (!project.isDisposed() && ProjectFileIndex.getInstance(project).isInContent(file)) {
                result.add(project);
            }
        }
        return result;
    }
}
