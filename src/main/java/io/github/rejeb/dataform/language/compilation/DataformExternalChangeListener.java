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
package io.github.rejeb.dataform.language.compilation;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.AsyncFileListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import io.github.rejeb.dataform.language.util.DataformProjectLayout;
import io.github.rejeb.dataform.language.util.DataformProjects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Recompiles a Dataform project when its sources change without the IDE writing them.
 *
 * <p>Everything the editor knows about a project — what each action publishes, what a column
 * resolves to — is read from a compilation of the sources as they were. A rollback, a checkout or a
 * pull replaces those sources behind the IDE's back, and until something asks for a new
 * compilation the project goes on being understood as it was before: a column the change brought
 * back reads as one no action declares. Typing in a file is what used to ask, which is why a
 * rollback appeared to need an edit before it took effect.</p>
 *
 * <p>Only changes the IDE learned about by looking at the disk are followed. Compiling saves the
 * sources first, and a save the plugin itself asked for must not be what starts the next
 * compilation.</p>
 */
public final class DataformExternalChangeListener implements AsyncFileListener {

    @Override
    public @Nullable ChangeApplier prepareChange(@NotNull List<? extends VFileEvent> events) {
        List<String> changedOnDisk = events.stream()
                .filter(VFileEvent::isFromRefresh)
                .filter(DataformExternalChangeListener::isSource)
                .map(VFileEvent::getPath)
                .toList();
        if (changedOnDisk.isEmpty()) return null;
        return new ChangeApplier() {
            @Override
            public void afterVfsChange() {
                DataformProjects.forEachOpen(project -> {
                    if (holds(project, changedOnDisk)) {
                        DataformAutoCompileService.getInstance(project).scheduleCompile();
                    }
                });
            }
        };
    }

    /**
     * Whether the event is about a source of a Dataform project.
     *
     * <p>The name is read from the path rather than from the file, because a file being created has
     * none yet and a checkout bringing a file back is exactly what this listener is here for. Where
     * there is a file, it also has to sit in a Dataform project. The directories holding
     * dependencies and build output are left out either way — an {@code npm install} writes
     * thousands of JavaScript files under {@code node_modules} and Dataform compiles none.</p>
     */
    private static boolean isSource(@NotNull VFileEvent event) {
        String path = event.getPath().replace('\\', '/');
        if (DataformProjectLayout.isUnderIgnoredDirectory(path)) return false;
        int slash = path.lastIndexOf('/');
        String name = slash < 0 ? path : path.substring(slash + 1);
        int dot = name.lastIndexOf('.');
        if (!DataformProjectLayout.isDataformSourceName(name,
                dot < 0 ? null : name.substring(dot + 1))) {
            return false;
        }
        VirtualFile file = event.getFile();
        return file == null || DataformProjectLayout.isInDataformProject(file);
    }

    /**
     * Whether one of the changed paths is inside the project. An application listener hears of every
     * open project at once, and compiling one because another was checked out costs a full Dataform
     * CLI run for nothing.
     */
    private static boolean holds(@NotNull Project project, @NotNull List<String> paths) {
        for (VirtualFile root : ProjectRootManager.getInstance(project).getContentRoots()) {
            String base = root.getPath().replace('\\', '/') + "/";
            if (paths.stream().anyMatch(path -> path.startsWith(base))) return true;
        }
        return false;
    }
}
