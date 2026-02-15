/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
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
import com.intellij.lang.javascript.psi.ecma6.TypeScriptModule;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import io.github.rejeb.dataform.setup.DataformInterpreterManager;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

@Service(Service.Level.PROJECT)
@State(name = "DataformCoreIndexService", storages = @Storage("DataformCoreIndexService.xml"))
public final class DataformCoreIndexService implements PersistentStateComponent<ServiceState> {
    private ServiceState state;
    private final Project project;
    private boolean notifyUser = false;

    public DataformCoreIndexService(Project project) {
        this.project = project;
        this.state = new ServiceState(
                null,
                -1,
                Optional.empty(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList()
        );
    }

    public static DataformCoreIndexService getInstance(Project project) {
        return project.getService(DataformCoreIndexService.class);
    }

    @Override
    public void initializeComponent() {
        DataformInterpreterManager dataformInterpreterManager = DataformInterpreterManager.getInstance(project);
        Optional<VirtualFile> corePackage = dataformInterpreterManager.dataformCorePath();

        String currentVersion = dataformInterpreterManager.currentDataformCoreVersion();

        var dataformCoreJsFile = findDataformCoreJsFile(corePackage);
        var cachedDataformFunctionsRef = this.findNonModuleElements(dataformCoreJsFile, JSFunction.class);
        var cachedDataformVariablesRef = this.findNonModuleElements(dataformCoreJsFile, JSVariable.class);
        var cachedDataformFunctionsForCompletion = cachedDataformFunctionsRef.stream()
                .map(DataformFunctionCompletionObject::fromJSFunction)
                .flatMap(Optional::stream)
                .toList();


        this.state = new ServiceState(
                currentVersion,
                System.currentTimeMillis(),
                dataformCoreJsFile,
                cachedDataformFunctionsRef,
                cachedDataformFunctionsRef.stream().map(JSFunction::getName).toList(),
                cachedDataformVariablesRef,
                cachedDataformVariablesRef.stream().map(JSVariable::getName).toList(),
                cachedDataformFunctionsForCompletion
        );
    }


    @Override
    @NotNull
    public ServiceState getState() {
        if (state.lastUpdate() == -1) {
            initializeComponent();
        }
        return this.state;
    }

    @Override
    public void loadState(@NotNull ServiceState state) {
        this.state = state;
    }

    public PsiFile getPsiFile() {
        if (getState().dataformCoreJsFile().isEmpty()) {
            notifyUserDataformNotInstalled(project);
            return null;
        }
        return state.dataformCoreJsFile().orElse(null);
    }

    @NotNull
    public Collection<String> getCachedDataformFunctionsNames() {
        if (getState().dataformCoreJsFile().isEmpty()) {
            notifyUserDataformNotInstalled(project);
            return Collections.emptyList();
        } else {
            return this.state.cachedDataformFunctionsNames();
        }
    }

    @NotNull
    public Collection<String> getCachedDataformVariablesNames() {
        if (getState().dataformCoreJsFile().isEmpty()) {
            notifyUserDataformNotInstalled(project);
            return Collections.emptyList();
        } else {
            return this.state.cachedDataformVariablesNames();
        }
    }

    @NotNull
    public Collection<JSFunction> getCachedDataformFunctionsRef() {
        if (getState().dataformCoreJsFile().isEmpty()) {
            notifyUserDataformNotInstalled(project);
            return Collections.emptyList();
        } else {
            return this.state.cachedDataformFunctionsRef();
        }
    }

    @NotNull
    public Collection<JSVariable> getCachedDataformVariablesRef() {
        if (getState().dataformCoreJsFile().isEmpty()) {
            notifyUserDataformNotInstalled(project);
            return Collections.emptyList();
        } else {
            return this.state.cachedDataformVariablesRef();
        }
    }

    @NotNull
    public Collection<DataformFunctionCompletionObject> getCachedDataformFunctionsForCompletion() {
        if (getState().dataformCoreJsFile().isEmpty()) {
            notifyUserDataformNotInstalled(project);
            return Collections.emptyList();
        } else {
            return this.state.cachedDataformFunctionsForCompletion();
        }
    }

    private Optional<PsiFile> findDataformCoreJsFile(Optional<VirtualFile> corePackage) {
        return corePackage
                .flatMap(coreDir ->
                        Optional.ofNullable(coreDir.findChild("bundle.d.ts"))
                                .map(child -> PsiManager.getInstance(project).findFile(child))
                );
    }

    private <T extends PsiElement> Collection<T> findNonModuleElements(
            Optional<PsiFile> dataformCoreJsFile,
            @NotNull Class<T> aClass) {
        return dataformCoreJsFile
                .map(tsFile -> PsiTreeUtil.findChildrenOfType(tsFile, aClass)
                        .stream()
                        .filter(elm -> PsiTreeUtil.getParentOfType(elm, TypeScriptModule.class) == null)
                        .toList())
                .orElse(Collections.emptyList());
    }

    private void notifyUserDataformNotInstalled(Project project) {
        if (this.notifyUser) {
            NotificationGroupManager.getInstance()
                    .getNotificationGroup("Dataform.Notifications")
                    .createNotification(
                            "Dataform not found",
                            "Please install @dataform/core globally: npm install -g @dataform/core",
                            NotificationType.WARNING
                    )
                    .notify(project);
            this.notifyUser = false;
        }
    }
}
