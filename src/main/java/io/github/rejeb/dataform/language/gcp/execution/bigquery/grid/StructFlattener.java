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
package io.github.rejeb.dataform.language.gcp.execution.bigquery.grid;

import com.google.cloud.bigquery.*;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class that flattens nested STRUCT fields into a flat column list
 * using dot-notation names (e.g. "address.city"), and pre-compiles row extractors
 * so that per-row value extraction requires no schema traversal.
 */
public final class StructFlattener {

    private StructFlattener() {}

    /**
     * Recursively flattens a list of BigQuery Fields into a flat list of (dotted-name, Field) pairs.
     */
    public static List<FlatField> flattenFields(@NotNull FieldList fields, @NotNull String prefix) {
        List<FlatField> result = new ArrayList<>();
        for (Field field : fields) {
            String qualifiedName = prefix.isEmpty() ? field.getName() : prefix + "." + field.getName();
            if (isStruct(field) && hasSubFields(field)) {
                result.addAll(flattenFields(field.getSubFields(), qualifiedName));
            } else {
                result.add(new FlatField(qualifiedName, field));
            }
        }
        return result;
    }

    /**
     * Pre-compiles one {@link RowExtractor} per leaf column from the schema.
     * Call this once per query result, then reuse the list for every row.
     */
    public static List<RowExtractor> buildExtractors(@NotNull FieldList fields) {
        List<RowExtractor> extractors = new ArrayList<>();
        buildExtractors(fields, new int[0], extractors);
        return extractors;
    }

    private static void buildExtractors(
            @NotNull FieldList fields,
            int[] parentPath,
            @NotNull List<RowExtractor> out
    ) {
        for (int i = 0; i < fields.size(); i++) {
            Field field   = fields.get(i);
            int[] path    = append(parentPath, i);
            if (isStruct(field) && hasSubFields(field)) {
                buildExtractors(field.getSubFields(), path, out);
            } else {
                out.add(new RowExtractor(path));
            }
        }
    }

    private static int[] append(int[] path, int index) {
        int[] next = new int[path.length + 1];
        System.arraycopy(path, 0, next, 0, path.length);
        next[path.length] = index;
        return next;
    }

    private static boolean isStruct(@NotNull Field field) {
        return field.getType().getStandardType() == StandardSQLTypeName.STRUCT;
    }

    private static boolean hasSubFields(@NotNull Field field) {
        return field.getSubFields() != null && !field.getSubFields().isEmpty();
    }

    private static Object toDisplayValue(@NotNull FieldValue fv) {
        return switch (fv.getAttribute()) {
            case RECORD    -> serializeRecord(fv.getRecordValue());
            case REPEATED  -> serializeRepeated(fv.getRepeatedValue());
            case RANGE     -> serializeRange(fv.getRangeValue());
            case PRIMITIVE -> fv.getValue();
        };
    }

    private static String serializeRecord(@NotNull FieldValueList record) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (FieldValue fv : record) {
            if (!first) sb.append(", ");
            first = false;
            sb.append(fv.isNull() ? "null" : toJsonValue(fv));
        }
        sb.append("}");
        return sb.toString();
    }

    private static String serializeRepeated(@NotNull List<FieldValue> repeated) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (FieldValue item : repeated) {
            if (!first) sb.append(", ");
            first = false;
            sb.append(item.isNull() ? "null" : toJsonValue(item));
        }
        sb.append("]");
        return sb.toString();
    }

    private static String toJsonValue(@NotNull FieldValue fv) {
        return switch (fv.getAttribute()) {
            case RECORD    -> serializeRecord(fv.getRecordValue());
            case REPEATED  -> serializeRepeated(fv.getRepeatedValue());
            case RANGE     -> "\"" + serializeRange(fv.getRangeValue()) + "\"";
            case PRIMITIVE -> {
                Object v = fv.getValue();
                yield v instanceof String ? "\"" + v + "\"" : String.valueOf(v);
            }
        };
    }

    private static String serializeRange(@NotNull Range range) {
        String start = range.getStart() != null && !range.getStart().isNull()
                ? String.valueOf(range.getStart().getValue()) : "UNBOUNDED";
        String end   = range.getEnd() != null && !range.getEnd().isNull()
                ? String.valueOf(range.getEnd().getValue()) : "UNBOUNDED";
        return "[" + start + ", " + end + ")";
    }

    /**
     * Holds a flattened field with its dot-notation display name and the original BigQuery Field.
     */
    public record FlatField(@NotNull String qualifiedName, @NotNull Field field) {}

    /**
     * Pre-compiled accessor for a single leaf value in a (potentially nested) FieldValueList.
     * {@code path} is the sequence of integer indexes to traverse from the root row downward.
     * No schema inspection occurs at extraction time — only index-based FieldValue traversal.
     */
    public static final class RowExtractor {

        private final int[] path;

        RowExtractor(int[] path) {
            this.path = path;
        }

        /**
         * Extracts the leaf value from a root-level FieldValueList using the pre-compiled path.
         */
        public Object extract(@NotNull FieldValueList root) {
            FieldValue current = root.get(path[0]);
            for (int depth = 1; depth < path.length; depth++) {
                if (current.isNull()) return null;
                current = current.getRecordValue().get(path[depth]);
            }
            return current.isNull() ? null : toDisplayValue(current);
        }
    }
}