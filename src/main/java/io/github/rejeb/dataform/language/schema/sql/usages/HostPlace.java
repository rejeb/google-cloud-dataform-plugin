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
package io.github.rejeb.dataform.language.schema.sql.usages;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * Where an occurrence sits in the host file: the offset to open, and the line holding it.
 *
 * <p>A place is taken before a row's text is, because a search hits the same line from several
 * references and only the first of them becomes a row. Taking the line text is what costs, so it is
 * left until the place is known to be new.</p>
 */
record HostPlace(@NotNull PsiFile host, @NotNull VirtualFile file, @NotNull Document document,
                 int offset, int line) {

    /**
     * What makes two occurrences one row. The window lists a line once, however many references of
     * the column that line holds.
     */
    @NotNull Key key() {
        return new Key(host.getName(), line);
    }

    /**
     * The line the occurrence sits on, as a view over the document rather than a copy of it. Taking
     * the whole file as a String for every row is what a search over a large project pays for.
     */
    @NotNull CharSequence lineText() {
        return document.getImmutableCharSequence()
                .subSequence(document.getLineStartOffset(line), document.getLineEndOffset(line));
    }

    /** How far into its own line the occurrence starts. */
    int columnInLine() {
        return offset - document.getLineStartOffset(line);
    }

    /** The file and line this place names, as {@code file.sqlx:23}. */
    @NotNull String location() {
        return host.getName() + ":" + (line + 1);
    }

    /**
     * A row's place, ordered as the window lists them. A parallel search finds rows in whatever
     * order its threads happen to finish, so the order has to come from the places themselves.
     */
    record Key(@NotNull String file, int line) implements Comparable<Key> {

        @Override
        public int compareTo(@NotNull Key other) {
            int byFile = file.compareTo(other.file);
            return byFile != 0 ? byFile : Integer.compare(line, other.line);
        }
    }
}
