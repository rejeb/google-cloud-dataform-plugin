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
package io.github.rejeb.dataform.language.gcp.toolwindow;

import com.intellij.openapi.vcs.FileStatus;
import com.intellij.ui.SimpleTextAttributes;
import io.github.rejeb.dataform.language.gcp.workspace.UncommittedChange.ChangeState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Shows the git state of a workspace file with the colours IntelliJ uses for version control.
 */
public final class WorkspaceFileStatuses {

    private WorkspaceFileStatuses() {
    }

    /**
     * The IntelliJ file status of a workspace change; unchanged when the state is unknown.
     */
    public static @NotNull FileStatus of(@Nullable ChangeState state) {
        if (state == null) {
            return FileStatus.NOT_CHANGED;
        }
        return switch (state) {
            case ADDED -> FileStatus.ADDED;
            case MODIFIED -> FileStatus.MODIFIED;
            case DELETED -> FileStatus.DELETED;
            case HAS_CONFLICTS -> FileStatus.MERGED_WITH_CONFLICTS;
            case UNKNOWN -> FileStatus.NOT_CHANGED;
        };
    }

    /**
     * Plain text in the colour of the state's file status; regular text when nothing changed.
     */
    public static @NotNull SimpleTextAttributes attributes(@Nullable ChangeState state) {
        FileStatus status = of(state);
        if (status == FileStatus.NOT_CHANGED) {
            return SimpleTextAttributes.REGULAR_ATTRIBUTES;
        }
        int style = status == FileStatus.DELETED ? SimpleTextAttributes.STYLE_STRIKEOUT : SimpleTextAttributes.STYLE_PLAIN;
        return new SimpleTextAttributes(style, status.getColor());
    }

    /**
     * The tooltip of a changed file, such as "Modified in the workspace"; {@code null} when nothing
     * changed.
     */
    public static @Nullable String tooltip(@Nullable ChangeState state) {
        if (state == null) {
            return null;
        }
        return switch (state) {
            case ADDED -> "Added in the workspace";
            case MODIFIED -> "Modified in the workspace";
            case DELETED -> "Deleted in the workspace";
            case HAS_CONFLICTS -> "Has conflicts in the workspace";
            case UNKNOWN -> null;
        };
    }
}
