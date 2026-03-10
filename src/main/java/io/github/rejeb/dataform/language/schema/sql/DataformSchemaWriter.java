/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
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

import com.intellij.database.model.RawDataSource;
import com.intellij.sql.actions.NavigationHelper;
import com.intellij.database.psi.DbDataSource;
import com.intellij.database.psi.DbPsiFacade;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Writes Dataform-derived table schemas as virtual objects into IntelliJ's
 * {@code external-data-<uuid>.xml} files so they appear in the Database tool window
 * and participate in native SQL reference resolution.
 *
 * <p>The format follows IntelliJ's virtual objects schema:
 * <pre>
 * &lt;virtual-objects&gt;
 *   &lt;object name="table" kind="TABLE" schema="dataset" catalog="project"&gt;
 *     &lt;column name="col" type="STRING" kind="COLUMN" not-null="false"/&gt;
 *   &lt;/object&gt;
 * &lt;/virtual-objects&gt;
 * </pre>
 *
 * <p>The file is written to {@code ~/.config/JetBrains/<IDE>/database/external-data-<uuid>.xml}.
 * IntelliJ's Database plugin watches this directory via a VFS file watcher and reloads
 * automatically after a VFS refresh.
 */
public final class DataformSchemaWriter {

    private static final Logger LOG = Logger.getInstance(DataformSchemaWriter.class);

    private DataformSchemaWriter() {}

    // ─────────────────────────────────────────────────────────────────────────
    // Public entry point
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Writes all schemas from {@code schemaCache} as virtual objects for the BigQuery
     * data source matching {@code projectId}.
     *
     * <p>Must be called from a background thread (called from
     * {@link DataformTableSchemaService#refreshAsync(CompiledGraph)}).
     */
    public static void writeSchemas(@NotNull Project project,
                                    @NotNull String projectId,
                                    @NotNull Map<String, List<ColumnInfo>> schemaCache) {
        if (schemaCache.isEmpty()) return;

        DbDataSource dataSource = findBigQueryDataSource(project, projectId);
        if (dataSource == null) return;

        String uuid = extractUuid(dataSource);
        if (uuid == null) {
            LOG.warn("Could not determine UUID for data source: " + dataSource.getName()
                    + " (delegate type: " + dataSource.getDelegateDataSource().getClass().getName() + ")");
            return;
        }

        try {
            Path externalDataFile = resolveExternalDataFile(uuid);
            Document doc = buildXmlDocument(schemaCache);
            writeXml(doc, externalDataFile.toFile());
            LOG.info("Written " + schemaCache.size() + " Dataform schemas → " + externalDataFile);

            triggerDataSourceReload(externalDataFile);

        } catch (Exception e) {
            LOG.warn("Failed to write external-data XML for project " + projectId, e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Data source matching
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Finds the BigQuery data source matching the given GCP project ID.
     * Three strategies are tried in order:
     * <ol>
     *   <li>JDBC URL contains the project ID</li>
     *   <li>Data source display name contains the project ID</li>
     *   <li>First BigQuery data source found (fallback)</li>
     * </ol>
     */
    @Nullable
    private static DbDataSource findBigQueryDataSource(@NotNull Project project,
                                                       @NotNull String projectId) {
        List<DbDataSource> sources = DbPsiFacade.getInstance(project).getDataSources();

        if (sources.isEmpty()) {
            LOG.debug("No data sources configured in the Database tool window");
            return null;
        }

        // Strategy 1: JDBC URL contains the project ID
        for (DbDataSource ds : sources) {
            try {
                String url = ds.getConnectionConfig().getUrl();
                if (url != null
                        && url.toLowerCase().contains("bigquery")
                        && url.contains(projectId)) {
                    System.out.println("Matched data source by URL: " + ds.getName());
                    return ds;
                }
            } catch (Exception ignored) {}
        }

        // Strategy 2: display name contains the project ID
        for (DbDataSource ds : sources) {
            if (ds.getName().contains(projectId)) {
                System.out.println("Matched data source by name: " + ds.getName());
                return ds;
            }
        }

        // Strategy 3: first BigQuery data source (single-project setups)
        for (DbDataSource ds : sources) {
            try {
                String url = ds.getConnectionConfig().getUrl();
                if (url != null && url.toLowerCase().contains("bigquery")) {
                    System.out.println("Matched first BigQuery data source: " + ds.getName());
                    return ds;
                }
            } catch (Exception ignored) {}
        }

        System.out.println("No BigQuery data source found for project '" + projectId
                + "'. Available data sources: "
                + sources.stream().map(DbDataSource::getName).toList());
        return null;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UUID extraction
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Extracts the UUID used for the {@code external-data-<uuid>.xml} file name.
     * {@code getDelegate()} returns a {@link RawDataSource} whose {@code getUniqueId()}
     * is the canonical UUID stored in {@code data-sources.xml}.
     */
    @Nullable
    private static String extractUuid(@NotNull DbDataSource dataSource) {
        RawDataSource delegate = dataSource.getDelegateDataSource();
        String uuid = delegate.getUniqueId();
        if (uuid != null && !uuid.isBlank()) return uuid;
        return null;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // File path
    // ─────────────────────────────────────────────────────────────────────────

    @NotNull
    private static Path resolveExternalDataFile(@NotNull String uuid) throws Exception {
        // ~/.config/JetBrains/<IDE>/database/
        Path dbDir = Path.of(PathManager.getConfigPath()).resolve("database");
        Files.createDirectories(dbDir);
        return dbDir.resolve("external-data-" + uuid + ".xml");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Reload trigger
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Notifies IntelliJ's VFS of the file change.
     * The Database plugin watches the {@code database/} directory via a file watcher
     * and will reload the virtual objects automatically after the VFS refresh.
     */
    private static void triggerDataSourceReload(@NotNull Path externalDataFile) {
        ApplicationManager.getApplication().invokeLater(() -> {
            VirtualFile vFile = LocalFileSystem.getInstance()
                    .refreshAndFindFileByNioFile(externalDataFile);
            if (vFile != null) {
                vFile.refresh(/*async=*/ true, /*recursive=*/ false);
                LOG.debug("VFS refreshed: " + vFile.getPath());
            } else {
                LOG.warn("VirtualFile not found after write: " + externalDataFile);
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // XML construction
    // ─────────────────────────────────────────────────────────────────────────

    @NotNull
    private static Document buildXmlDocument(@NotNull Map<String, List<ColumnInfo>> schemaCache)
            throws Exception {

        Document doc = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .newDocument();

        Element root = doc.createElement("virtual-objects");
        doc.appendChild(root);

        for (Map.Entry<String, List<ColumnInfo>> entry : schemaCache.entrySet()) {
            String fqn = entry.getKey();
            String[] parts = fqn.split("\\.", 3);
            if (parts.length != 3) {
                LOG.debug("Skipping FQN with unexpected format (expected catalog.schema.table): " + fqn);
                continue;
            }

            String catalog = parts[0]; // GCP project ID
            String schema  = parts[1]; // BigQuery dataset
            String name    = parts[2]; // BigQuery table name

            Element obj = doc.createElement("object");
            obj.setAttribute("name",    name);
            obj.setAttribute("kind",    "TABLE");
            obj.setAttribute("schema",  schema);
            obj.setAttribute("catalog", catalog);

            for (ColumnInfo col : entry.getValue()) {
                appendColumn(doc, obj, col, "");
            }

            root.appendChild(obj);
        }

        return doc;
    }

    /**
     * Recursively appends column elements, flattening RECORD sub-fields with
     * dot-prefixed names (e.g., {@code address.city}) since IntelliJ's
     * virtual-objects format does not support nested column elements natively.
     */
    private static void appendColumn(@NotNull Document doc,
                                     @NotNull Element parent,
                                     @NotNull ColumnInfo col,
                                     @NotNull String namePrefix) {
        String fullName = namePrefix.isEmpty() ? col.name() : namePrefix + "." + col.name();

        Element colEl = doc.createElement("column");
        colEl.setAttribute("name",     fullName);
        colEl.setAttribute("type",     toXmlType(col));
        colEl.setAttribute("kind",     "COLUMN");
        colEl.setAttribute("not-null", "REQUIRED".equals(col.mode()) ? "true" : "false");
        parent.appendChild(colEl);

        // Recurse for RECORD (STRUCT) sub-fields
        for (ColumnInfo sub : col.subFields()) {
            appendColumn(doc, parent, sub, fullName);
        }
    }

    @NotNull
    private static String toXmlType(@NotNull ColumnInfo col) {
        if (col.isRepeated()) return "ARRAY";
        return switch (col.type()) {
            case "INTEGER" -> "INT64";
            case "FLOAT"   -> "FLOAT64";
            case "BOOLEAN" -> "BOOL";
            case "RECORD"  -> "STRUCT";
            default        -> col.type();
        };
    }

    // ─────────────────────────────────────────────────────────────────────────
    // XML writing
    // ─────────────────────────────────────────────────────────────────────────

    private static void writeXml(@NotNull Document doc, @NotNull File file) throws Exception {
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT,   "yes");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        transformer.transform(new DOMSource(doc), new StreamResult(file));
    }
}
