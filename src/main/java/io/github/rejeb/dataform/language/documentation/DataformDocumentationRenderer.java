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
package io.github.rejeb.dataform.language.documentation;

import com.intellij.lang.documentation.DocumentationMarkup;
import com.intellij.openapi.util.text.StringUtil;
import io.github.rejeb.dataform.language.documentation.bigquery.BigQueryFunctionDoc;
import io.github.rejeb.dataform.language.schema.sql.model.ColumnInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class DataformDocumentationRenderer {

    private static final String NULLABLE_MODE = "NULLABLE";

    private DataformDocumentationRenderer() {
    }

    /**
     * Renders the documentation popup content of a Dataform table, view, declaration or operation.
     */
    @NotNull
    public static String renderTable(@NotNull String displayName,
                                     @Nullable String fullName,
                                     @Nullable String type,
                                     @Nullable String sourceFile,
                                     @Nullable String description,
                                     @NotNull List<ColumnInfo> columns) {
        StringBuilder builder = new StringBuilder();

        builder.append(DocumentationMarkup.DEFINITION_START)
                .append("<b>").append(escape(displayName)).append("</b>");
        if (isNotBlank(fullName)) {
            builder.append("<br/>").append(DocumentationMarkup.GRAYED_START)
                    .append(escape(fullName)).append(DocumentationMarkup.GRAYED_END);
        }
        builder.append(DocumentationMarkup.DEFINITION_END);

        if (isNotBlank(description)) {
            builder.append(DocumentationMarkup.CONTENT_START)
                    .append(escape(description))
                    .append(DocumentationMarkup.CONTENT_END);
        }

        StringBuilder sections = new StringBuilder();
        appendSection(sections, "Type", escape(type));
        appendSection(sections, "Source", escape(sourceFile));
        if (!columns.isEmpty()) {
            appendSection(sections, "Columns", renderColumnTable(columns));
        }
        appendSections(builder, sections);

        return builder.toString();
    }

    /**
     * Renders the documentation popup content of a single BigQuery column.
     */
    @NotNull
    public static String renderColumn(@NotNull ColumnInfo column, @Nullable String tableName) {
        StringBuilder builder = new StringBuilder();

        builder.append(DocumentationMarkup.DEFINITION_START)
                .append("<b>").append(escape(column.name())).append("</b>")
                .append(" ").append(DocumentationMarkup.GRAYED_START)
                .append(escape(column.type())).append(DocumentationMarkup.GRAYED_END)
                .append(DocumentationMarkup.DEFINITION_END);

        if (isNotBlank(column.description())) {
            builder.append(DocumentationMarkup.CONTENT_START)
                    .append(escape(column.description()))
                    .append(DocumentationMarkup.CONTENT_END);
        }

        StringBuilder sections = new StringBuilder();
        if (isNotBlank(column.mode()) && !NULLABLE_MODE.equalsIgnoreCase(column.mode())) {
            appendSection(sections, "Mode", escape(column.mode()));
        }
        appendSection(sections, "Table", escape(tableName));
        if (!column.subFields().isEmpty()) {
            appendSection(sections, "Fields", renderColumnTable(column.subFields()));
        }
        appendSections(builder, sections);

        return builder.toString();
    }

    /**
     * Renders the documentation popup content of a SQLX config block key: what the schema says
     * about it, how it is filled in, and the columns available to it when it names one.
     */
    @NotNull
    public static String renderConfigKey(@NotNull String key,
                                         @Nullable String type,
                                         @Nullable String description,
                                         @Nullable String usage,
                                         @NotNull List<ColumnInfo> availableColumns) {
        StringBuilder builder = new StringBuilder();

        builder.append(DocumentationMarkup.DEFINITION_START)
                .append("<b>").append(escape(key)).append("</b>");
        if (isNotBlank(type)) {
            builder.append(" ").append(DocumentationMarkup.GRAYED_START)
                    .append(escape(type)).append(DocumentationMarkup.GRAYED_END);
        }
        builder.append(DocumentationMarkup.DEFINITION_END);

        if (isNotBlank(description)) {
            builder.append(DocumentationMarkup.CONTENT_START)
                    .append(escape(description))
                    .append(DocumentationMarkup.CONTENT_END);
        }

        StringBuilder sections = new StringBuilder();
        if (isNotBlank(usage)) {
            appendSection(sections, "Usage", "<pre>" + escape(usage) + "</pre>");
        }
        if (!availableColumns.isEmpty()) {
            appendSection(sections, "Columns", renderColumnTable(availableColumns));
        }
        appendSections(builder, sections);

        return builder.toString();
    }

    /**
     * Renders the documentation popup content of a BigQuery builtin function.
     */
    @NotNull
    public static String renderFunction(@NotNull BigQueryFunctionDoc doc) {
        StringBuilder builder = new StringBuilder();

        builder.append(DocumentationMarkup.DEFINITION_START);
        for (int i = 0; i < doc.signatures().size(); i++) {
            if (i > 0) {
                builder.append("<br/>");
            }
            builder.append("<b>").append(escape(doc.signatures().get(i))).append("</b>");
        }
        builder.append(DocumentationMarkup.DEFINITION_END);

        builder.append(DocumentationMarkup.CONTENT_START)
                .append(escape(doc.description()))
                .append(DocumentationMarkup.CONTENT_END);

        StringBuilder sections = new StringBuilder();
        appendSection(sections, "Returns", escape(doc.returns()));
        appendSection(sections, "Category", escape(doc.category()));
        appendSection(sections, "Documentation",
                "<a href=\"" + escape(doc.docUrl()) + "\">BigQuery reference</a>");
        appendSections(builder, sections);

        return builder.toString();
    }

    private static String renderColumnTable(List<ColumnInfo> columns) {
        StringBuilder table = new StringBuilder("<table>");
        for (ColumnInfo column : columns) {
            table.append("<tr><td><code>").append(escape(column.name())).append("</code></td>")
                    .append("<td>").append(escape(column.type()));
            if (column.isRepeated()) {
                table.append(" []");
            }
            table.append("</td><td>");
            if (isNotBlank(column.description())) {
                table.append(escape(column.description()));
            }
            table.append("</td></tr>");
            for (ColumnInfo subField : column.subFields()) {
                table.append("<tr><td>&nbsp;&nbsp;<code>")
                        .append(escape(subField.name())).append("</code></td>")
                        .append("<td>").append(escape(subField.type())).append("</td><td>");
                if (isNotBlank(subField.description())) {
                    table.append(escape(subField.description()));
                }
                table.append("</td></tr>");
            }
        }
        return table.append("</table>").toString();
    }

    private static void appendSection(StringBuilder sections, String header, @Nullable String value) {
        if (!isNotBlank(value)) {
            return;
        }
        sections.append(DocumentationMarkup.SECTION_HEADER_START)
                .append(header)
                .append(DocumentationMarkup.SECTION_SEPARATOR)
                .append(value)
                .append(DocumentationMarkup.SECTION_END);
    }

    private static void appendSections(StringBuilder builder, StringBuilder sections) {
        if (sections.isEmpty()) {
            return;
        }
        builder.append(DocumentationMarkup.SECTIONS_START)
                .append(sections)
                .append(DocumentationMarkup.SECTIONS_END);
    }

    private static boolean isNotBlank(@Nullable String value) {
        return value != null && !value.isBlank();
    }

    @Nullable
    private static String escape(@Nullable String value) {
        return value == null ? null : StringUtil.escapeXmlEntities(value);
    }
}
