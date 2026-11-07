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
package io.github.rejeb.dataform.language.unittest;

import com.intellij.ide.FileIconProvider;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import io.github.rejeb.dataform.language.DataformIcons;
import io.github.rejeb.dataform.language.SqlxFileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;

/**
 * Gives Dataform unit test files, the SQLX files whose config block declares {@code type: "test"},
 * the test file icon in the project views, the editor tabs and the file popups. The config block is
 * read from the text of the file, unsaved edits included and without any PSI, so the icon follows the
 * file as it is edited, before any compilation, and on any thread.
 */
public final class TestFileIconProvider implements FileIconProvider, DumbAware {

    private static final int TEXT_LIMIT = 64 * 1024;

    @Override
    public @Nullable Icon getIcon(@NotNull VirtualFile file, int flags, @Nullable Project project) {
        if (project == null || project.isDisposed() || file.isDirectory()
                || !FileTypeRegistry.getInstance().isFileOfType(file, SqlxFileType.INSTANCE)) {
            return null;
        }
        Document document = FileDocumentManager.getInstance().getCachedDocument(file);
        CharSequence text = document != null ? document.getImmutableCharSequence() : LoadTextUtil.loadText(file, TEXT_LIMIT);
        return SqlxUnitTests.isUnitTestText(text) ? DataformIcons.TEST_FILE : null;
    }
}
