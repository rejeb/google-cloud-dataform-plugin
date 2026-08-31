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

import com.intellij.openapi.vfs.AsyncFileListener;
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
        boolean changedOnDisk = events.stream()
                .anyMatch(event -> event.isFromRefresh()
                        && DataformProjectLayout.isDataformSource(event.getFile()));
        if (!changedOnDisk) return null;
        return new ChangeApplier() {
            @Override
            public void afterVfsChange() {
                DataformProjects.forEachOpen(project ->
                        DataformAutoCompileService.getInstance(project).scheduleCompile());
            }
        };
    }
}
