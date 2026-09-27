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
package io.github.rejeb.dataform.language.unittest.creation;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.AppExecutorUtil;
import io.github.rejeb.dataform.language.compilation.DataformCompilationService;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.compilation.model.CompiledTable;
import io.github.rejeb.dataform.language.compilation.model.Target;
import io.github.rejeb.dataform.language.unittest.TestableActions;
import io.github.rejeb.dataform.language.unittest.schema.TestSchemaResolver;
import io.github.rejeb.dataform.language.util.DataformProjectLayout;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Creates {@code definitions/tests/test_<action>.sqlx} for the tables and views a SQLX or JS file
 * declares, asking which ones when the file declares several.
 */
public final class CreateDataformTestAction extends AnAction implements DumbAware {

    private static final Set<String> EXTENSIONS = Set.of("sqlx", "js");

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
        e.getPresentation().setEnabledAndVisible(project != null && !testableActions(project, file).isEmpty());
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
        CompiledGraph graph = project == null ? null : DataformCompilationService.getInstance(project).getCompiledGraph();
        VirtualFile definitions = definitionsDirOf(file);
        if (graph == null || definitions == null) {
            return;
        }
        List<CompiledTable> actions = testableActions(project, file);
        VirtualFile testsDir = definitions.findChild(TestFileNames.TESTS_DIR);
        Map<CompiledTable, String> names = new LinkedHashMap<>();
        for (CompiledTable action : actions) {
            Target tested = TestFileNames.testedTarget(action);
            names.put(action, TestFileNames.fileName(tested, TestFileNames.isAmbiguous(graph, tested)));
        }
        Set<CompiledTable> existing = names.entrySet().stream()
                .filter(entry -> testsDir != null && testsDir.findChild(entry.getValue()) != null)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        List<CompiledTable> chosen = actions.size() == 1
                ? actions
                : TestableActionChooser.choose(project, actions, existing);
        if (chosen.isEmpty()) {
            return;
        }
        ReadAction.nonBlocking(() -> contents(project, graph, chosen, names))
                .finishOnUiThread(ModalityState.defaultModalityState(), contents -> write(project, definitions, contents))
                .submit(AppExecutorUtil.getAppExecutorService());
    }

    /**
     * Returns the actions of the given file that Dataform accepts a unit test for, empty when the
     * file is no SQLX or JS file under a {@code definitions} directory or when no graph is compiled.
     */
    @NotNull
    public static List<CompiledTable> testableActions(@NotNull Project project, @Nullable VirtualFile file) {
        if (file == null || file.isDirectory() || !EXTENSIONS.contains(file.getExtension())
                || definitionsDirOf(file) == null) {
            return List.of();
        }
        CompiledGraph graph = DataformCompilationService.getInstance(project).getCompiledGraph();
        return graph == null ? List.of() : TestableActions.in(graph, file);
    }

    /**
     * Returns the closest {@code definitions} directory holding the given file, or null.
     */
    @Nullable
    public static VirtualFile definitionsDirOf(@Nullable VirtualFile file) {
        for (VirtualFile dir = file == null ? null : file.getParent(); dir != null; dir = dir.getParent()) {
            if (DataformProjectLayout.DEFINITIONS_DIR.equals(dir.getName())) {
                return dir;
            }
        }
        return null;
    }

    private static Map<String, String> contents(@NotNull Project project,
                                                @NotNull CompiledGraph graph,
                                                @NotNull List<CompiledTable> chosen,
                                                @NotNull Map<CompiledTable, String> names) {
        TestSchemaResolver resolver = TestSchemaResolver.getInstance(project);
        Map<String, String> contents = new LinkedHashMap<>();
        for (CompiledTable action : chosen) {
            Target tested = TestFileNames.testedTarget(action);
            List<Target> inputs = action.getDependencyTargets() == null ? List.of() : action.getDependencyTargets();
            contents.put(names.get(action), TestFileContent.of(tested,
                    inputs, target -> TestFileNames.isAmbiguous(graph, target),
                    target -> target == tested ? resolver.columnsOf(action.getTarget()) : resolver.columnsOf(target)));
        }
        return contents;
    }

    private static void write(@NotNull Project project, @NotNull VirtualFile definitions,
                              @NotNull Map<String, String> contents) {
        List<VirtualFile> created = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        WriteCommandAction.runWriteCommandAction(project, "Create Dataform Test", null, () -> {
            try {
                VirtualFile tests = VfsUtil.createDirectoryIfMissing(definitions, TestFileNames.TESTS_DIR);
                for (Map.Entry<String, String> entry : contents.entrySet()) {
                    if (tests.findChild(entry.getKey()) != null) {
                        skipped.add(entry.getKey());
                        continue;
                    }
                    VirtualFile file = tests.createChildData(CreateDataformTestAction.class, entry.getKey());
                    VfsUtil.saveText(file, entry.getValue());
                    created.add(file);
                }
            } catch (IOException exception) {
                notify(project, "Could not create the test: " + exception.getMessage(), NotificationType.ERROR);
            }
        });
        created.forEach(file -> FileEditorManager.getInstance(project).openFile(file, true));
        if (!skipped.isEmpty()) {
            notify(project, "Existing tests left untouched: " + String.join(", ", skipped), NotificationType.INFORMATION);
        }
    }

    private static void notify(@NotNull Project project, @NotNull String message, @NotNull NotificationType type) {
        NotificationGroupManager.getInstance().getNotificationGroup("Dataform.Notifications")
                .createNotification(message, type)
                .notify(project);
    }
}
