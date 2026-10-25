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
package io.github.rejeb.dataform.language.setup;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs {@code dataform install} in the directories created by the new project wizard once the
 * project is opened.
 */
public final class DataformPendingPackageInstallActivity implements ProjectActivity {

    static final String PENDING_DIRS_KEY = "dataform.pendingPackageInstallDirs";
    private static final Object LOCK = new Object();

    @Override
    public @Nullable Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
        installPending(project);
        return null;
    }

    /**
     * Installs the Dataform packages of the given directory: right away when the project is already
     * opened, otherwise as soon as its startup activities run.
     */
    public static void schedule(@NotNull Project project, @NotNull VirtualFile projectDir) {
        synchronized (LOCK) {
            PropertiesComponent properties = PropertiesComponent.getInstance(project);
            List<String> stored = properties.getList(PENDING_DIRS_KEY);
            List<String> pending = stored == null ? new ArrayList<>() : new ArrayList<>(stored);
            if (!pending.contains(projectDir.getUrl())) {
                pending.add(projectDir.getUrl());
            }
            properties.setList(PENDING_DIRS_KEY, pending);
        }
        if (StartupManager.getInstance(project).postStartupActivityPassed()) {
            installPending(project);
        }
    }

    private static void installPending(@NotNull Project project) {
        List<String> pending;
        synchronized (LOCK) {
            PropertiesComponent properties = PropertiesComponent.getInstance(project);
            pending = properties.getList(PENDING_DIRS_KEY);
            properties.setList(PENDING_DIRS_KEY, List.of());
        }
        if (pending == null || pending.isEmpty() || project.isDisposed()) {
            return;
        }
        for (String url : pending) {
            VirtualFile dir = VirtualFileManager.getInstance().findFileByUrl(url);
            if (dir != null && dir.isValid()) {
                DataformPackageInstaller.getInstance(project).installAsync(dir);
            }
        }
    }
}
