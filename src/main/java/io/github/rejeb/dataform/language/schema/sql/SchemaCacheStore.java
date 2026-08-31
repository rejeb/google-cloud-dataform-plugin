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
package io.github.rejeb.dataform.language.schema.sql;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import io.github.rejeb.dataform.language.schema.sql.model.ColumnInfo;
import io.github.rejeb.dataform.language.schema.sql.model.DataformDasTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Holds the extracted schemas: the working cache the extraction writes into, the immutable
 * snapshot resolution reads, and the serialized form kept between IDE runs.
 */
final class SchemaCacheStore {

    private static final Logger LOG = Logger.getInstance(SchemaCacheStore.class);
    private static final Gson GSON = new GsonBuilder().create();
    private static final Type CACHE_TYPE = new TypeToken<Map<String, SchemaCacheEntry>>() {
    }.getType();
    private static final long UNKNOWN_MODIFICATION_TIME = 0L;
    private static final long NO_DOCUMENT = -1L;

    private final Project project;
    private final Map<String, DataformDasTable> tables = new ConcurrentHashMap<>();
    private final Map<String, List<ColumnInfo>> guesses = new ConcurrentHashMap<>();
    private final Map<VirtualFile, Long> guessedFrom = new ConcurrentHashMap<>();
    private final Map<String, Long> modificationTimes = new ConcurrentHashMap<>();
    private final Map<String, String> fileNames = new ConcurrentHashMap<>();

    private volatile Map<String, DataformDasTable> publishedTables = Map.of();
    private DataformTableSchemaService.State currentState = new DataformTableSchemaService.State();

    SchemaCacheStore(@NotNull Project project) {
        this.project = project;
    }

    /**
     * The resolved tables as an immutable snapshot, replaced as a whole when an extraction run
     * ends. Callers must never see the working cache directly: SQL reference resolution reads this
     * map inside {@code ResolveCache}, which computes a result twice and reports a non-idempotent
     * computation when the two runs disagree, so a map growing entry by entry under a running
     * extraction would make resolution report a different set of columns from one run to the next.
     */
    @NotNull
    Map<String, DataformDasTable> published() {
        return publishedTables;
    }

    /** Publishes the working cache as the new snapshot returned by {@link #published()}. */
    void publish() {
        publishedTables = Collections.unmodifiableMap(new LinkedHashMap<>(tables));
    }

    /** Whether a schema is cached for the action, whatever its age. */
    boolean contains(@NotNull String fqn) {
        return tables.containsKey(fqn);
    }

    /**
     * Stores a freshly extracted schema together with the time of the source it was read from.
     */
    void put(@NotNull String fqn,
             @NotNull String tableName,
             @NotNull java.util.List<ColumnInfo> columns,
             @Nullable String fileName) {
        tables.put(fqn, buildTable(tableName, columns, fileName));
        if (guesses.remove(fqn) != null && guesses.isEmpty()) guessedFrom.clear();
        if (fileName == null) return;
        fileNames.put(fqn, fileName);
        String basePath = project.getBasePath();
        if (basePath != null) {
            modificationTimes.put(fqn, ActionSourceFiles.modificationTime(basePath, fileName));
        }
    }

    /**
     * Renames a column of a cached schema, leaving everything else about the entry alone.
     *
     * <p>The time the schema was read at is deliberately not touched: the source file has just been
     * written and is now newer than it, which is what makes the next extraction read the action
     * again and confirm — or correct — what was written here.</p>
     *
     * @param columnPath the column to rename, a dotted path for a field of a struct
     * @return whether the schema held that column
     */
    boolean rename(@NotNull String fqn,
                   @NotNull String columnPath,
                   @NotNull String newName) {
        DataformDasTable table = tables.get(fqn);
        if (table == null) return false;
        List<ColumnInfo> columns = renamed(table.getColumns(), columnPath, newName);
        if (columns == null) return false;
        guesses.putIfAbsent(fqn, table.getColumns());
        tables.put(fqn, buildTable(table.getName(), columns, fileNames.get(fqn)));
        return true;
    }

    /**
     * Records the state the files a rename wrote were left in, which is what its guesses are worth.
     */
    void guessedFrom(@NotNull java.util.Collection<VirtualFile> written) {
        for (VirtualFile file : written) {
            guessedFrom.put(file, stampOf(file));
        }
    }

    /** Whether any schema currently holds what a rename wrote rather than what was read. */
    boolean hasGuesses() {
        return !guesses.isEmpty();
    }

    /**
     * Puts back what the last extraction read, once a file the rename wrote no longer holds what it
     * left there.
     *
     * <p>A guess is a claim about files, so the files are what settle it. Saving the rename to disk
     * leaves the documents it was written in exactly as they were, while anything that puts other
     * text in one — an undo, a rollback, the next edit — gives it a new stamp. The rename is one
     * act, so one file disowning it drops the whole of it rather than half.</p>
     *
     * @return whether any schema changed
     */
    boolean dropStaleGuesses() {
        if (guesses.isEmpty() || !isDisowned()) return false;
        for (Map.Entry<String, List<ColumnInfo>> entry : guesses.entrySet()) {
            tables.put(entry.getKey(),
                    buildTable(tableNameOf(entry.getKey()), entry.getValue(),
                            fileNames.get(entry.getKey())));
        }
        guesses.clear();
        guessedFrom.clear();
        return true;
    }

    private boolean isDisowned() {
        return guessedFrom.entrySet().stream()
                .anyMatch(entry -> stampOf(entry.getKey()) != entry.getValue());
    }

    /**
     * The modification stamp of a file's document, or {@link #NO_DOCUMENT} when it has none. The
     * document is read rather than the file: a rename writes documents, and the save that follows
     * changes the file on disk without changing what was written.
     */
    private static long stampOf(@NotNull VirtualFile file) {
        Document document = FileDocumentManager.getInstance().getCachedDocument(file);
        return document == null ? NO_DOCUMENT : document.getModificationStamp();
    }

    /**
     * The columns with the one named by {@code path} renamed, or {@code null} when none of them is.
     */
    private static @Nullable List<ColumnInfo> renamed(@NotNull List<ColumnInfo> columns,
                                                      @NotNull String path,
                                                      @NotNull String newName) {
        int dot = path.indexOf('.');
        String head = dot < 0 ? path : path.substring(0, dot);
        List<ColumnInfo> result = new ArrayList<>(columns.size());
        boolean found = false;
        for (ColumnInfo column : columns) {
            if (!column.name().equalsIgnoreCase(head)) {
                result.add(column);
                continue;
            }
            if (dot < 0) {
                result.add(new ColumnInfo(newName, column.type(), column.mode(),
                        column.description(), column.subFields()));
                found = true;
                continue;
            }
            List<ColumnInfo> fields = renamed(column.subFields(), path.substring(dot + 1), newName);
            if (fields == null) {
                result.add(column);
                continue;
            }
            result.add(new ColumnInfo(column.name(), column.type(), column.mode(),
                    column.description(), fields));
            found = true;
        }
        return found ? result : null;
    }

    /** Drops the schemas of actions the compiled graph no longer declares. */
    void retainOnly(@NotNull Set<String> known) {
        tables.keySet().retainAll(known);
        fileNames.keySet().retainAll(known);
        modificationTimes.keySet().retainAll(known);
    }

    DataformTableSchemaService.@Nullable State state() {
        return currentState;
    }

    /** Restores the schemas persisted by a previous IDE run. */
    void load(@NotNull DataformTableSchemaService.State state) {
        this.currentState = state;
        if (state.schemaCacheJson == null || state.schemaCacheJson.isBlank()) return;
        try {
            Map<String, SchemaCacheEntry> loaded = GSON.fromJson(state.schemaCacheJson, CACHE_TYPE);
            if (loaded == null) return;
            clear();
            loaded.forEach(this::restoreEntry);
            LOG.info("Restored " + tables.size() + " schemas from persistent state");
        } catch (Exception e) {
            LOG.warn("Failed to deserialize schema cache from state: " + e.getMessage());
            clear();
            this.currentState = new DataformTableSchemaService.State();
        }
        publish();
    }

    /** Serializes the working cache into the state persisted between IDE runs. */
    void persist() {
        Map<String, SchemaCacheEntry> toSerialize = toCacheEntries(tables, modificationTimes, fileNames);
        currentState.schemaCacheJson = GSON.toJson(toSerialize);
        LOG.info("Persisted " + toSerialize.size() + " schemas to state");
    }

    /**
     * The persisted modification time of an action, or {@code null} when it has none to trust. The
     * persisted snapshot is read rather than the working cache on purpose: a refresh is planned
     * against what the last completed run recorded.
     */
    @Nullable
    Long persistedModificationTime(@NotNull String fqn) {
        if (currentState.schemaCacheJson == null) return null;
        try {
            Map<String, SchemaCacheEntry> parsed = GSON.fromJson(currentState.schemaCacheJson, CACHE_TYPE);
            if (parsed == null) return null;
            SchemaCacheEntry entry = parsed.get(fqn);
            if (entry == null || !isKnownModificationTime(entry.lastModified())) return null;
            return entry.lastModified();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Serializes the cached schemas together with the modification time of the source they were
     * extracted from. An action whose source time is unknown — because its dry-run failed, or
     * because it was not part of this run — is written as unknown rather than as a time no file can
     * ever exceed: pinning it would make the planner report it as up to date forever, so it would
     * never be extracted again.
     */
    @NotNull
    static Map<String, SchemaCacheEntry> toCacheEntries(@NotNull Map<String, DataformDasTable> tables,
                                                        @NotNull Map<String, Long> modificationTimes,
                                                        @NotNull Map<String, String> fileNames) {
        return tables.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> new SchemaCacheEntry(
                                e.getValue().getColumns(),
                                modificationTimes.getOrDefault(e.getKey(), UNKNOWN_MODIFICATION_TIME),
                                fileNames.get(e.getKey()))
                ));
    }

    /**
     * Whether a persisted modification time can be trusted. Times written by an older build as
     * {@link Long#MAX_VALUE} are treated as unknown so the actions they pinned are extracted again.
     */
    static boolean isKnownModificationTime(long lastModified) {
        return lastModified > UNKNOWN_MODIFICATION_TIME && lastModified < Long.MAX_VALUE;
    }

    private void restoreEntry(@NotNull String fqn, @NotNull SchemaCacheEntry entry) {
        tables.put(fqn, buildTable(tableNameOf(fqn), entry.columns(), entry.fileName()));
        if (entry.fileName() != null) fileNames.put(fqn, entry.fileName());
        if (isKnownModificationTime(entry.lastModified())) {
            modificationTimes.put(fqn, entry.lastModified());
        }
    }

    private void clear() {
        tables.clear();
        guesses.clear();
        guessedFrom.clear();
        fileNames.clear();
        modificationTimes.clear();
    }

    @NotNull
    private DataformDasTable buildTable(@NotNull String tableName,
                                        @NotNull java.util.List<ColumnInfo> columns,
                                        @Nullable String fileName) {
        return new DataformDasTable(PsiManager.getInstance(project), tableName, columns,
                resolveSourceFile(fileName));
    }

    @Nullable
    private VirtualFile resolveSourceFile(@Nullable String fileName) {
        if (fileName == null) return null;
        String basePath = project.getBasePath();
        if (basePath == null) return null;
        return LocalFileSystem.getInstance().findFileByPath(basePath + "/" + fileName);
    }

    @NotNull
    private static String tableNameOf(@NotNull String fqn) {
        int idx = fqn.lastIndexOf('.');
        return idx >= 0 ? fqn.substring(idx + 1) : fqn;
    }
}
