/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.rejeb.dataform.language.gcp.toolwindow;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import io.github.rejeb.dataform.language.gcp.toolwindow.action.PullFromWorkspaceAction;
import io.github.rejeb.dataform.language.gcp.toolwindow.action.PushToWorkspaceAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.function.Supplier;

public class FileViewToolbar extends JPanel {

    public FileViewToolbar(
            @NotNull Project project,
            @NotNull Supplier<@Nullable String> workspaceIdSupplier,
            @NotNull DataformGcpPanel.PanelCallback callback
    ) {
        super(new BorderLayout());

        DefaultActionGroup group = new DefaultActionGroup();
        group.add(new PullFromWorkspaceAction(workspaceIdSupplier, callback));
        group.add(new PushToWorkspaceAction(workspaceIdSupplier, callback));

        ActionToolbar toolbar = ActionManager.getInstance()
                .createActionToolbar("DataformFileViewToolbar", group, true);
        toolbar.setTargetComponent(this);

        add(toolbar.getComponent(), BorderLayout.CENTER);
    }
}
