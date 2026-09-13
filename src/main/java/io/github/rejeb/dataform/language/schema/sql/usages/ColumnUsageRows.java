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

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.sql.psi.SqlCompositeElementTypes;
import io.github.rejeb.dataform.language.schema.sql.ColumnOriginService;
import io.github.rejeb.dataform.language.schema.sql.model.ColumnInfo;
import io.github.rejeb.dataform.language.schema.sql.model.StructColumnPath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Builds the lines of the column window: what the column is built from, then what reads it,
 * grouped under Declaration and Usages headings carrying their counts.
 *
 * <p>A struct column also lists the fields it holds, down to the last leaf, because a column a
 * query cannot read on its own is best explained by what it can read.</p>
 *
 * <p>Everything the reference search finds reads the column, so it is a usage. The declarations
 * are the columns this one is built from, which the file states rather than a search finding: a
 * rename has one, an expression over several columns has one for each.</p>
 *
 * <p>The search is bounded and runs under a read action. It is the reference search Find Usages
 * already runs, so a row here and a row in the Find window come from the same place.</p>
 *
 * <p>How the reads are found depends on what the window was opened on, which
 * {@link ColumnUsageSearch} decides. A column of a table is searched for in parallel, which the
 * reference search does across the files the
 * column's name appears in once it is allowed to. What that costs is the injected SQL built for each
 * of those files and the resolve of every reference in it, and those are what the cores are spent
 * on. A parallel search reports its finds in no particular order, so the rows are ordered by the
 * place they sit in rather than by the order they arrived.</p>
 */
public final class ColumnUsageRows {

    private static final Logger LOG = Logger.getInstance(ColumnUsageRows.class);

    /**
     * Ceiling on the reads collected when the window opens. The window lists what a reader can take
     * in, and the search stops once it has that much rather than walking every file the name
     * appears in. The reader asks for the rest when the first rows are not enough.
     */
    static final int MAX_READS = 20;

    /** The ceiling to pass for every read the project holds, however many there are. */
    static final int UNBOUNDED = Integer.MAX_VALUE;

    /** Headings in the order the window shows them. */
    public static final String DECLARATION = "DECLARATION";
    public static final String FIELDS = "FIELDS";
    public static final String USAGES = "USAGES";

    private ColumnUsageRows() {
    }

    /**
     * The rows for a column, headings included. Empty when the column has neither a declaration
     * that can be located nor a single read.
     *
     * <p>Runs the search where it is called, and blocks for as long as it takes. Callers on the
     * event thread go through {@link ColumnUsageRowsLoader} instead, which runs it off that
     * thread.</p>
     */
    public static @NotNull List<ColumnUsageRow> of(@NotNull Project project,
                                                   @NotNull ColumnWindowTarget target,
                                                   @Nullable PsiElement caretReference) {
        return of(project, target, caretReference, MAX_READS);
    }

    /**
     * The rows for a column with the reads collected up to {@code maxReads}, which is
     * {@link #UNBOUNDED} for every read the project holds. A Usages heading says whether the search
     * stopped at the ceiling.
     */
    public static @NotNull List<ColumnUsageRow> of(@NotNull Project project,
                                                   @NotNull ColumnWindowTarget target,
                                                   @Nullable PsiElement caretReference,
                                                   int maxReads) {
        return ReadAction.computeBlocking(() -> collect(project, target, caretReference, maxReads));
    }

    private static @NotNull List<ColumnUsageRow> collect(@NotNull Project project,
                                                         @NotNull ColumnWindowTarget target,
                                                         @Nullable PsiElement caretReference,
                                                         int maxReads) {
        HostPlaceLocator locator = new HostPlaceLocator(project);
        String name = target.name();

        Map<HostPlace.Key, ColumnUsageRow> declarations =
                declarations(project, target, caretReference, locator, name);
        Map<HostPlace.Key, ColumnUsageRow> fields = fields(project, target, locator);
        Map<HostPlace.Key, ColumnUsageRow> usages = new ConcurrentHashMap<>();
        AtomicInteger collected = new AtomicInteger();
        search(project, target, caretReference, locator, name, declarations.keySet(), usages,
                collected, maxReads);

        List<ColumnUsageRow> rows = new ArrayList<>();
        if (!declarations.isEmpty()) {
            rows.add(ColumnUsageRow.heading(DECLARATION, declarations.size(), false));
            rows.addAll(declarations.values());
        }
        if (!fields.isEmpty()) {
            rows.add(ColumnUsageRow.heading(FIELDS, fields.size(), false));
            rows.addAll(fields.values());
        }
        if (!usages.isEmpty()) {
            rows.add(ColumnUsageRow.heading(USAGES, usages.size(),
                    collected.get() >= maxReads));
            rows.addAll(ordered(usages));
        }
        return rows;
    }

    /**
     * The columns this one is built from, in the order the file states them. There are a handful at
     * most and no search finds them, so they are collected where the window is asked for.
     */
    private static @NotNull Map<HostPlace.Key, ColumnUsageRow> declarations(
            @NotNull Project project, @NotNull ColumnWindowTarget target,
            @Nullable PsiElement caretReference, @NotNull HostPlaceLocator locator,
            @NotNull String name) {
        Map<HostPlace.Key, ColumnUsageRow> declarations = new LinkedHashMap<>();
        for (PsiElement declaration : target.declarations()) {
            if (isSameElement(declaration, caretReference) || isSelf(declaration, target)) continue;
            Located located = Located.of(locator, declaration);
            if (located == null || declarations.containsKey(located.place().key())) continue;
            ColumnUsageRow row = row(project, located, name, DECLARATION,
                    ColumnUsageRow.Kind.DECLARATION);
            if (row != null) declarations.put(located.place().key(), row);
        }
        return declarations;
    }

    /**
     * The fields a struct holds, each in the file writing it, down to the last leaf.
     *
     * <p>A field holding fields of its own contributes those rather than itself: what a reader can
     * navigate to, and what a query can read, is a leaf. They are listed in the order the struct
     * declares them, which is the order the file they come from writes them in.</p>
     */
    private static @NotNull Map<HostPlace.Key, ColumnUsageRow> fields(
            @NotNull Project project, @NotNull ColumnWindowTarget target,
            @NotNull HostPlaceLocator locator) {
        StructColumnPath path = target.structPath();
        if (path == null || !path.leaf().isRecord()) return Map.of();

        ColumnOriginService origins = ColumnOriginService.getInstance(project);
        Map<HostPlace.Key, ColumnUsageRow> fields = new LinkedHashMap<>();
        for (StructColumnPath leaf : leavesOf(path)) {
            PsiElement declaring = origins.declaringElement(leaf);
            if (declaring == null) continue;
            Located located = Located.of(locator, declaring);
            if (located == null || fields.containsKey(located.place().key())) continue;
            ColumnUsageRow row = row(project, located, leaf.leafName(), FIELDS,
                    ColumnUsageRow.Kind.FIELD);
            if (row != null) fields.put(located.place().key(), row);
        }
        return fields;
    }

    /** Every leaf inside a struct, at any depth. */
    private static @NotNull List<StructColumnPath> leavesOf(@NotNull StructColumnPath path) {
        List<StructColumnPath> leaves = new ArrayList<>();
        collectLeaves(path, leaves);
        return leaves;
    }

    private static void collectLeaves(@NotNull StructColumnPath path,
                                      @NotNull List<StructColumnPath> leaves) {
        for (ColumnInfo field : path.leaf().subFields()) {
            List<ColumnInfo> trail = new ArrayList<>(path.trail());
            trail.add(field);
            StructColumnPath child = new StructColumnPath(path.root(), trail);
            if (field.isRecord() && !field.subFields().isEmpty()) {
                collectLeaves(child, leaves);
            } else {
                leaves.add(child);
            }
        }
    }

    /**
     * Collects the reads of the column, across the threads the reference search runs on.
     *
     * <p>Every piece of state the search touches is either immutable or concurrent, which is what
     * parallel processing asks of a processor. The places already taken are handed over as a
     * snapshot: they are settled before a single thread starts, and a snapshot says so.</p>
     */
    private static void search(@NotNull Project project, @NotNull ColumnWindowTarget target,
                               @Nullable PsiElement caretReference,
                               @NotNull HostPlaceLocator locator, @NotNull String name,
                               @NotNull Set<HostPlace.Key> keys,
                               @NotNull Map<HostPlace.Key, ColumnUsageRow> usages,
                               @NotNull AtomicInteger collected, int maxReads) {
        Set<HostPlace.Key> declared = Set.copyOf(keys);
        AtomicInteger seen = new AtomicInteger();
        AtomicInteger skippedSelf = new AtomicInteger();
        AtomicInteger unlocated = new AtomicInteger();
        AtomicInteger duplicate = new AtomicInteger();
        AtomicInteger noRow = new AtomicInteger();
        ColumnUsageSearch.of(project, target).forEachRead(element -> {
            seen.incrementAndGet();
            if (collected.get() >= maxReads) return false;
            if (isSameElement(element, caretReference) || isSelf(element, target)) {
                skippedSelf.incrementAndGet();
                return true;
            }
            Located located = Located.of(locator, element);
            if (located == null) {
                unlocated.incrementAndGet();
                return true;
            }
            HostPlace.Key key = located.place().key();
            if (declared.contains(key) || usages.containsKey(key)) {
                duplicate.incrementAndGet();
                return true;
            }
            ColumnUsageRow row = row(project, located, name, USAGES, ColumnUsageRow.Kind.USAGE);
            if (row == null) noRow.incrementAndGet();
            if (row != null && usages.putIfAbsent(key, row) == null) {
                collected.incrementAndGet();
            }
            return collected.get() < maxReads;
        }, maxReads);
        LOG.info("DIAG column rows: name=" + name + " structPath=" + target.structPath()
                + " searchTargets=" + target.searchTargets().size()
                + " declarations=" + keys.size() + " seen=" + seen + " skippedSelf=" + skippedSelf
                + " unlocated=" + unlocated + " duplicate=" + duplicate + " noRow=" + noRow
                + " usages=" + usages.size());
    }

    /** The reads, by the file and line they sit in, which is the order the window lists them. */
    private static @NotNull List<ColumnUsageRow> ordered(
            @NotNull Map<HostPlace.Key, ColumnUsageRow> usages) {
        List<HostPlace.Key> keys = new ArrayList<>(usages.keySet());
        keys.sort(Comparator.naturalOrder());
        List<ColumnUsageRow> rows = new ArrayList<>(keys.size());
        for (HostPlace.Key key : keys) rows.add(usages.get(key));
        return rows;
    }

    /**
     * Whether an element is the column's own name where it is written.
     *
     * <p>An alias is both what later queries read and the place it is written, so the search finds
     * it and would list the column as reading itself. The caret reference does not catch it: a
     * column reference is what the caret usually sits on, and an alias is not one.</p>
     */
    private static boolean isSelf(@NotNull PsiElement element, @NotNull ColumnWindowTarget target) {
        for (PsiElement searched : target.searchTargets()) {
            if (isSameElement(element, searched)) return true;
        }
        return false;
    }

    /**
     * Whether two elements are the same occurrence. The reference search reports an element of its
     * own for the place the caret sits on, so identity alone does not recognise it.
     */
    private static boolean isSameElement(@NotNull PsiElement element, @Nullable PsiElement other) {
        if (other == null) return false;
        if (element == other) return true;
        if (element.getContainingFile() != other.getContainingFile()) return false;
        return element.getTextRange().intersects(other.getTextRange());
    }

    /**
     * A row for one occurrence: the line it sits on, split around the column name so the name can
     * be drawn apart from the expression that reads it.
     */
    private static @Nullable ColumnUsageRow row(@NotNull Project project,
                                                @NotNull Located located,
                                                @NotNull String columnName,
                                                @NotNull String group,
                                                @NotNull ColumnUsageRow.Kind kind) {
        HostPlace place = located.place();
        CharSequence text = place.lineText();
        int at = place.columnInLine();
        if (at < 0 || at > text.length()) return null;

        String shown = nameOf(located.identifier(), located.element(), columnName);
        String before = text.subSequence(0, at).toString().stripLeading();
        int end = Math.min(text.length(), at + shown.length());
        String after = text.subSequence(end, text.length()).toString();

        return ColumnUsageRow.entry(kind, group, before, shown, after, place.location(),
                new OpenFileDescriptor(project, place.file(), place.offset()));
    }

    /**
     * The name a row shows, which is the name at that place and not the name the window was opened
     * on: a column is declared under the name its own file gives it, and saying otherwise tells the
     * reader the declaring table has a column it does not have.
     */
    private static @NotNull String nameOf(@Nullable PsiElement identifier,
                                          @NotNull PsiElement element,
                                          @NotNull String fallback) {
        PsiElement source = identifier != null ? identifier : element;
        String text = source.getText();
        if (text == null || text.isEmpty() || text.contains("\n")) return fallback;
        return text.replace("`", "");
    }

    private static @Nullable PsiElement lastIdentifier(@NotNull PsiElement element) {
        PsiElement last = null;
        for (PsiElement child : element.getChildren()) {
            if (child.getNode() != null
                    && child.getNode().getElementType() == SqlCompositeElementTypes.SQL_IDENTIFIER) {
                last = child;
            }
        }
        return last;
    }

    /**
     * An occurrence placed in its host file, before its row's text is taken. Holding the identifier
     * alongside the place keeps the name the row shows from being looked for twice.
     */
    private record Located(@NotNull PsiElement element, @Nullable PsiElement identifier,
                           @NotNull HostPlace place) {

        static @Nullable Located of(@NotNull HostPlaceLocator locator, @NotNull PsiElement element) {
            PsiElement identifier = lastIdentifier(element);
            HostPlace place = locator.locate(identifier == null ? element : identifier);
            return place == null ? null : new Located(element, identifier, place);
        }
    }
}
