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
package io.github.rejeb.dataform.language.columns.rename.downstream;

import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;
import io.github.rejeb.dataform.language.util.DataformNotifications;
import org.jetbrains.annotations.NotNull;

/**
 * Tells the user that downstream actions still read a renamed column under its old name, with a
 * link renaming those reads.
 */
public final class DownstreamRenameNotifier {

    private DownstreamRenameNotifier() {
    }

    /**
     * The text of the balloon.
     *
     * @param readers how many downstream actions still fail on the old name
     */
    public static @NotNull String textOf(@NotNull DownstreamRename rename, int readers) {
        return rename.describe() + ": " + readers + " downstream "
                + (readers == 1 ? "action still reads" : "actions still read") + " `" + rename.oldName() + "`";
    }

    /**
     * Shows the balloon. Called on the event thread.
     */
    public static void show(@NotNull Project project, @NotNull DownstreamRename rename, int readers) {
        DataformNotifications.create(textOf(rename, readers), NotificationType.WARNING)
                .addAction(NotificationAction.createSimpleExpiring("Rename downstream usages",
                        () -> DownstreamRenameFix.run(project, rename)))
                .notify(project);
    }
}
