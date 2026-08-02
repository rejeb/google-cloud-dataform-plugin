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
package io.github.rejeb.dataform.language.projectView;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ProjectViewNodeDecorator;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import io.github.rejeb.dataform.language.DataformIcons;
import io.github.rejeb.dataform.language.compilation.DataformDisabledActions;
import org.jetbrains.annotations.NotNull;

/**
 * Marks SQLX files whose Dataform action declares {@code disabled: true} in the project views. The
 * file keeps its place in the tree, but its icon is greyed out and a {@code disabled} hint is added
 * next to the name, so a file that is never executed is recognisable without opening it.
 */
public final class DisabledActionNodeDecorator implements ProjectViewNodeDecorator, DumbAware {

    private static final String HINT = "disabled";

    @Override
    public void decorate(@NotNull ProjectViewNode<?> node, @NotNull PresentationData data) {
        VirtualFile file = node.getVirtualFile();
        if (file == null || file.isDirectory()) return;

        Project project = node.getProject();
        if (project == null || project.isDisposed()) return;

        if (!DataformDisabledActions.getInstance(project).isDisabled(file)) return;

        data.setIcon(DataformIcons.FILE_DISABLED);
        data.setLocationString(HINT);
    }
}
