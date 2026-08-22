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

import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPsiElementPointer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * One line of the column window: either a group heading, or a place the column is declared or read.
 *
 * <p>An entry keeps the expression split around the column name so the name can be drawn apart from
 * the rest, and names the file and line it sits in rather than the project it belongs to.</p>
 */
public final class ColumnUsageRow {

    /** What a row stands for. The heading of a group, or a place within it. */
    public enum Kind {
        HEADING,
        DECLARATION,
        USAGE
    }

    private final Kind kind;
    private final String group;
    private final String heading;
    private final int count;
    private final String before;
    private final String name;
    private final String after;
    private final String location;
    private final SmartPsiElementPointer<PsiElement> target;

    private ColumnUsageRow(Kind kind, String group, String heading, int count, String before,
                           String name, String after, String location,
                           SmartPsiElementPointer<PsiElement> target) {
        this.kind = kind;
        this.group = group;
        this.heading = heading;
        this.count = count;
        this.before = before;
        this.name = name;
        this.after = after;
        this.location = location;
        this.target = target;
    }

    static @NotNull ColumnUsageRow heading(@NotNull String heading, int count) {
        return new ColumnUsageRow(Kind.HEADING, heading, heading, count, "", "", "", "", null);
    }

    static @NotNull ColumnUsageRow entry(@NotNull Kind kind, @NotNull String group,
                                         @NotNull String before, @NotNull String name,
                                         @NotNull String after, @NotNull String location,
                                         @NotNull SmartPsiElementPointer<PsiElement> target) {
        return new ColumnUsageRow(kind, group, "", 0, before, name, after, location, target);
    }

    public @NotNull Kind kind() {
        return kind;
    }

    public boolean isHeading() {
        return kind == Kind.HEADING;
    }

    public @NotNull String heading() {
        return heading;
    }

    /** The heading this row sits under, which is the heading's own name for a heading. */
    public @NotNull String group() {
        return group;
    }

    public int count() {
        return count;
    }

    public @NotNull String before() {
        return before;
    }

    public @NotNull String name() {
        return name;
    }

    public @NotNull String after() {
        return after;
    }

    /** The file and line this row points at, as {@code file.sqlx:23}. */
    public @NotNull String location() {
        return location;
    }

    /** The element to open, or {@code null} for a heading and for a row gone stale. */
    public @Nullable PsiElement target() {
        return target == null ? null : target.getElement();
    }

    @Override
    public String toString() {
        return isHeading() ? heading + " (" + count + ")" : before + name + after + "  " + location;
    }
}
