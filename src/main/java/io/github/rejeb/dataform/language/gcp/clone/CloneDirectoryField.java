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
package io.github.rejeb.dataform.language.gcp.clone;

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.OSAgnosticPathUtil;
import com.intellij.ui.DocumentAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.DocumentEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * The directory of the new project. It follows the chosen repository until the user edits it, and
 * must be missing or empty.
 */
final class CloneDirectoryField {

    private final TextFieldWithBrowseButton field = new TextFieldWithBrowseButton();
    private final String parent;
    private boolean following;
    private boolean edited;

    CloneDirectoryField(@NotNull Project project, @NotNull String parent) {
        this.parent = parent;
        field.addBrowseFolderListener(project.isDefault() ? null : project,
                FileChooserDescriptorFactory.createSingleFolderDescriptor().withTitle("Directory of the Project"));
        field.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent e) {
                edited |= !following;
            }
        });
    }

    @NotNull TextFieldWithBrowseButton component() {
        return field;
    }

    void follow(@Nullable String directoryName) {
        if (edited || directoryName == null) {
            return;
        }
        following = true;
        try {
            field.setText(FileUtil.join(parent, directoryName));
        } finally {
            following = false;
        }
    }

    @NotNull Path path() {
        return Path.of(OSAgnosticPathUtil.expandUserHome(field.getText().trim()));
    }

    @Nullable String problem() {
        if (field.getText().isBlank()) {
            return "Choose a directory for the project";
        }
        Path directory;
        try {
            directory = path();
        } catch (InvalidPathException e) {
            return "This is not a valid path";
        }
        if (!Files.exists(directory)) {
            return null;
        }
        if (!Files.isDirectory(directory)) {
            return "This path is a file, not a directory";
        }
        try (Stream<Path> children = Files.list(directory)) {
            return children.findAny().isPresent() ? "This directory is not empty" : null;
        } catch (IOException e) {
            return "This directory cannot be read";
        }
    }
}
