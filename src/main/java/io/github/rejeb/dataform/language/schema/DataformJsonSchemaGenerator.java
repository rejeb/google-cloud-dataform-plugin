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
package io.github.rejeb.dataform.language.schema;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.intellij.json.JsonLanguage;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFileFactory;
import org.jetbrains.annotations.NotNull;

import java.util.*;

@Service(Service.Level.PROJECT)
public final class DataformJsonSchemaGenerator {

    private static final Logger LOGGER = Logger.getInstance(DataformJsonSchemaGenerator.class.getName());

    private static final Map<String, String> SQLX_TYPES = new LinkedHashMap<>();

    static {
        SQLX_TYPES.put("table", "ActionConfig.TableConfig");
        SQLX_TYPES.put("view", "ActionConfig.ViewConfig");
        SQLX_TYPES.put("incremental", "ActionConfig.IncrementalTableConfig");
        SQLX_TYPES.put("assertion", "ActionConfig.AssertionConfig");
        SQLX_TYPES.put("operations", "ActionConfig.OperationConfig");
        SQLX_TYPES.put("declaration", "ActionConfig.DeclarationConfig");
    }

    private static final Set<String> SQLX_EXCLUDED_FIELDS = Set.of(
            "preOperations", "postOperations", "filename");

    private static final Set<String> SQLX_BIGQUERY_TYPES = Set.of("table", "view", "incremental");

    private static final Set<String> WORKFLOW_REQUIRED = Set.of(
            "defaultProject", "defaultDataset", "defaultLocation", "defaultAssertionDataset");

    private static final String SQLX_COLUMN_DEF_KEY = "SqlxColumnDescriptor";

    private final Map<String, ProtoModel.ProtoMessage> messageIndex = new HashMap<>();
    private final Map<String, ProtoModel.ProtoEnum> enumIndex = new HashMap<>();
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final Project project;
    private Optional<VirtualFile> configSchemaFile = Optional.empty();
    private Optional<VirtualFile> workflowSettingsSchemaFile = Optional.empty();

    public DataformJsonSchemaGenerator(@NotNull Project project) {
        this.project = project;
        init();
    }

    private void init() {
        ProtoParser protoParser = this.project.getService(ProtoParser.class);
        if (messageIndex.isEmpty() || enumIndex.isEmpty()) {
            messageIndex.clear();
            enumIndex.clear();
            protoParser.parse().ifPresent(protoFile -> {
                indexMessages(protoFile.messages);
                indexEnums(protoFile.enums);
            });
        }
    }

    public Optional<VirtualFile> generateSqlxConfigSchema() {
        ProtoParser protoParser = this.project.getService(ProtoParser.class);
        if (!protoParser.configProtoFileExists()) {
            return Optional.empty();
        } else if (configSchemaFile.isEmpty()) {
            try {
                this.configSchemaFile = Optional.of(PsiFileFactory.getInstance(project)
                        .createFileFromText("sqlx_config_schema.json",
                                JsonLanguage.INSTANCE,
                                mapper.writeValueAsString(buildSqlxConfigSchema()))
                        .getVirtualFile());
            } catch (JsonProcessingException e) {
                LOGGER.error("Unable to serialize sqlx config schema", e);
            }
        }
        return this.configSchemaFile;
    }

    public Optional<VirtualFile> generateWorkflowSettingsSchema() {
        ProtoParser protoParser = this.project.getService(ProtoParser.class);
        if (!protoParser.configProtoFileExists()) {
            return Optional.empty();
        } else if (workflowSettingsSchemaFile.isEmpty()) {
            try {
                this.workflowSettingsSchemaFile = Optional.of(PsiFileFactory.getInstance(project)
                        .createFileFromText("workflow_settings_schema.json",
                                JsonLanguage.INSTANCE,
                                mapper.writeValueAsString(buildWorkflowSettingsSchema()))
                        .getVirtualFile());
            } catch (JsonProcessingException e) {
                LOGGER.error("Unable to serialize workflow settings schema", e);
            }
        }
        return this.workflowSettingsSchemaFile;
    }

    public ObjectNode buildWorkflowSettingsSchema() {
        ProtoModel.ProtoMessage msg = requireMessage("WorkflowSettings");
        Map<String, ObjectNode> defs = new LinkedHashMap<>();
        collectDefs(msg, defs, new HashSet<>());

        ObjectNode root = obj();
        root.put("$schema", "http://json-schema.org/draft-07/schema#");
        root.put("$id", "https://dataform.co/schemas/workflow_settings.json");
        root.put("title", "Dataform Workflow Settings");
        root.put("description", "Schema for the workflow_settings.yaml configuration file.");
        root.put("type", "object");
        root.put("additionalProperties", false);

        List<String> required = msg.fields.stream()
                .filter(f -> f.description.startsWith("Required.") ||
                        WORKFLOW_REQUIRED.contains(f.camelName))
                .map(f -> f.camelName)
                .toList();
        if (!required.isEmpty()) {
            ArrayNode req = root.putArray("required");
            required.forEach(req::add);
        }

        ObjectNode props = root.putObject("properties");
        for (ProtoModel.ProtoField field : msg.fields) {
            props.set(field.camelName, fieldToSchema(field, msg.qualifiedName, defs));
        }

        if (!defs.isEmpty()) {
            ObjectNode defsNode = root.putObject("$defs");
            defs.forEach(defsNode::set);
        }
        return root;
    }

    public ObjectNode buildSqlxConfigSchema() {
        Map<String, ObjectNode> defs = new LinkedHashMap<>();

        defs.put(SQLX_COLUMN_DEF_KEY, buildSqlxColumnDescriptorDef());

        for (String qualifiedName : SQLX_TYPES.values()) {
            collectDefs(requireMessage(qualifiedName), defs, new HashSet<>());
        }

        ObjectNode root = obj();
        root.put("$schema", "http://json-schema.org/draft-07/schema#");
        root.put("$id", "https://dataform.co/schemas/sqlx_config.json");
        root.put("title", "Dataform SQLX Config Block");
        root.put("description", "Schema for the config{} block in Dataform SQLX files.");
        root.put("type", "object");
        root.putArray("required").add("type");

        ArrayNode oneOf = root.putArray("oneOf");
        for (Map.Entry<String, String> entry : SQLX_TYPES.entrySet()) {
            oneOf.add(buildActionBranch(entry.getKey(), requireMessage(entry.getValue()), defs));
        }

        if (!defs.isEmpty()) {
            ObjectNode defsNode = root.putObject("$defs");
            defs.forEach(defsNode::set);
        }
        return root;
    }

    private ObjectNode buildActionBranch(String sqlxType,
                                         ProtoModel.ProtoMessage msg,
                                         Map<String, ObjectNode> defs) {
        ObjectNode branch = obj();
        branch.put("type", "object");
        branch.put("additionalProperties", false);
        branch.putArray("required").add("type");
        if (!msg.description.isEmpty()) branch.put("description", msg.description);

        ObjectNode props = branch.putObject("properties");

        ObjectNode typeFixed = props.putObject("type");
        typeFixed.put("type", "string");
        typeFixed.putArray("enum").add(sqlxType);
        typeFixed.put("description", "Action type: " + sqlxType + ".");

        for (ProtoModel.ProtoField field : msg.fields) {

            if (SQLX_EXCLUDED_FIELDS.contains(field.camelName)) continue;

            if ("columns".equals(field.camelName)) {
                props.set("columns", buildSqlxColumnsProperty());
                continue;
            }

            ObjectNode fieldSchema = fieldToSchema(field, msg.qualifiedName, defs);

            if ("dependencyTargets".equals(field.camelName)) {
                props.set("dependencyTargets", fieldSchema);
                ObjectNode depsAlias = obj();
                depsAlias.put("type", "array");
                depsAlias.put("description",
                        "Shorthand dependency names (alternative to dependencyTargets).");
                depsAlias.putObject("items").put("type", "string");
                props.set("dependencies", depsAlias);
                continue;
            }

            props.set(field.camelName, fieldSchema);

            if ("dataset".equals(field.camelName)) {
                ObjectNode alias = fieldSchema.deepCopy();
                alias.put("description", "Alias for dataset. The schema/dataset of the action.");
                props.set("schema", alias);
            }

            if ("project".equals(field.camelName)) {
                ObjectNode alias = fieldSchema.deepCopy();
                alias.put("description",
                        "Alias for project. The Google Cloud project (database) of the action.");
                props.set("database", alias);
            }
        }

        if (SQLX_BIGQUERY_TYPES.contains(sqlxType)) {
            props.set("bigquery", buildBigQueryWrapper(sqlxType));
        }

        return branch;
    }

    private ObjectNode buildBigQueryWrapper(String sqlxType) {
        ObjectNode bq = obj();
        bq.put("type", "object");
        bq.put("description",
                "BigQuery-specific options. These can also be set directly at the top level.");
        bq.put("additionalProperties", false);
        ObjectNode props = bq.putObject("properties");

        // partitionBy: string | { field, dataType, granularity? }
        ObjectNode partitionBy = props.putObject("partitionBy");
        partitionBy.put("description",
                "Partition key. Accepts a column name string or a structured object.");
        ArrayNode pbOneOf = partitionBy.putArray("oneOf");
        pbOneOf.addObject()
                .put("type", "string")
                .put("description", "Simple expression, e.g. 'DATE(ts_col)'.");
        ObjectNode pbObj = pbOneOf.addObject();
        pbObj.put("type", "object");
        pbObj.putArray("required").add("field").add("dataType");
        ObjectNode pbProps = pbObj.putObject("properties");
        pbProps.putObject("field")
                .put("type", "string").put("description", "Partition column name.");
        ObjectNode dataType = pbProps.putObject("dataType");
        dataType.put("type", "string").put("description", "Data type of the partition column.");
        dataType.putArray("enum").add("timestamp").add("date").add("datetime").add("int64");
        ObjectNode granularity = pbProps.putObject("granularity");
        granularity.put("type", "string").put("description", "Temporal partitioning granularity.");
        granularity.putArray("enum").add("hour").add("day").add("month").add("year");

        ObjectNode clusterBy = props.putObject("clusterBy");
        clusterBy.put("type", "array").put("description", "Clustering columns (max 4).");
        clusterBy.putObject("items").put("type", "string");

        props.putObject("partitionExpirationDays")
                .put("type", "integer")
                .put("description", "Days before partition data expiry.");

        props.putObject("requirePartitionFilter")
                .put("type", "boolean")
                .put("description", "Requires a WHERE clause filter on the partition column.");

        if ("incremental".equals(sqlxType)) {
            props.putObject("updatePartitionFilter")
                    .put("type", "string")
                    .put("description", "SQL filter applied during incremental updates.");
        }

        props.putObject("labels")
                .put("type", "object")
                .put("description", "BigQuery resource labels (key-value pairs).")
                .putObject("additionalProperties").put("type", "string");

        props.putObject("additionalOptions")
                .put("type", "object")
                .put("description", "Extra BigQuery API options passed verbatim.")
                .putObject("additionalProperties").put("type", "string");

        if (!"view".equals(sqlxType)) {
            props.set("iceberg", buildIcebergObject());
        }

        return bq;
    }

    private ObjectNode buildIcebergObject() {
        ObjectNode iceberg = obj();
        iceberg.put("type", "object");
        iceberg.put("description", "Apache Iceberg options for BigLake Metastore tables.");
        iceberg.put("additionalProperties", false);
        ObjectNode props = iceberg.putObject("properties");
        props.putObject("connection")
                .put("type", "string")
                .put("description", "Cloud Storage connection resource name.");
        ObjectNode fileFormat = props.putObject("fileFormat");
        fileFormat.put("type", "string").put("description", "Storage file format.");
        fileFormat.putArray("enum").add("PARQUET");
        props.putObject("bucketName")
                .put("type", "string").put("description", "Cloud Storage bucket name.");
        props.putObject("tableFolderRoot")
                .put("type", "string").put("description", "Root folder inside the bucket.");
        props.putObject("tableFolderSubpath")
                .put("type", "string").put("description", "Sub-folder path for this table.");
        return iceberg;
    }

    private ObjectNode buildSqlxColumnsProperty() {
        ObjectNode schema = obj();
        schema.put("type", "object");
        schema.put("description",
                "Column descriptions. Map of column name to a string or nested record descriptor.");
        schema.putObject("additionalProperties")
                .put("$ref", "#/$defs/" + SQLX_COLUMN_DEF_KEY);
        return schema;
    }

    private ObjectNode buildSqlxColumnDescriptorDef() {
        ObjectNode def = obj();
        def.put("description",
                "A column descriptor: a plain string description or a nested record descriptor.");
        ArrayNode oneOf = def.putArray("oneOf");

        oneOf.addObject().put("type", "string");

        ObjectNode record = oneOf.addObject();
        record.put("type", "object");
        record.put("additionalProperties", false);
        ObjectNode recProps = record.putObject("properties");

        recProps.putObject("description")
                .put("type", "string")
                .put("description", "Text description of the column or record.");

        ObjectNode nestedCols = recProps.putObject("columns");
        nestedCols.put("type", "object")
                .put("description", "Nested column descriptions for record types.");
        nestedCols.putObject("additionalProperties")
                .put("$ref", "#/$defs/" + SQLX_COLUMN_DEF_KEY);

        ObjectNode policyTags = recProps.putObject("bigqueryPolicyTags");
        policyTags.put("type", "array")
                .put("description", "BigQuery policy tags applied to the column.");
        policyTags.putObject("items").put("type", "string");

        ObjectNode tags = recProps.putObject("tags");
        tags.put("type", "array").put("description", "User-defined tags for this column.");
        tags.putObject("items").put("type", "string");

        return def;
    }

    private ObjectNode fieldToSchema(ProtoModel.ProtoField field,
                                     String contextQualified,
                                     Map<String, ObjectNode> defs) {
        ObjectNode schema;
        if (field.isMap) {
            schema = obj();
            schema.put("type", "object");
            schema.set("additionalProperties",
                    scalarOrRefSchema(field.mapValueType, contextQualified, defs));
        } else if (field.repeated) {
            schema = obj();
            schema.put("type", "array");
            schema.set("items", scalarOrRefSchema(field.type, contextQualified, defs));
        } else {
            schema = scalarOrRefSchema(field.type, contextQualified, defs);
        }
        if (!field.description.isEmpty()) schema.put("description", field.description);
        return schema;
    }

    private ObjectNode scalarOrRefSchema(String type, String contextQualified,
                                         Map<String, ObjectNode> defs) {
        switch (type) {
            case "string":
                return scalar("string");
            case "bool":
                return scalar("boolean");
            case "int32":
            case "int64":
            case "uint32":
            case "uint64":
            case "sint32":
            case "sint64":
            case "fixed32":
            case "fixed64":
            case "sfixed32":
            case "sfixed64":
                return scalar("integer");
            case "float":
            case "double":
                return scalar("number");
            case "bytes":
                return scalar("string");
            case "google.protobuf.Struct":
                return obj().put("type", "object");
        }

        String resolvedEnum = resolveTypeName(type, contextQualified, enumIndex);
        if (resolvedEnum != null) {
            ProtoModel.ProtoEnum protoEnum = enumIndex.get(resolvedEnum);
            ObjectNode enumSchema = obj();
            enumSchema.put("type", "string");
            ArrayNode values = enumSchema.putArray("enum");
            protoEnum.values.forEach(values::add);
            return enumSchema;
        }

        String resolvedMsg = resolveTypeName(type, contextQualified, messageIndex);
        if (resolvedMsg != null) {
            String defKey = resolvedMsg.replace('.', '_');
            if (!defs.containsKey(defKey)) {
                defs.put(defKey, obj()); // placeholder to break circular refs
                defs.put(defKey, buildMessageSchema(messageIndex.get(resolvedMsg), defs));
            }
            return obj().put("$ref", "#/$defs/" + defKey);
        }

        return obj().put("type", "object").put("description", "Unknown proto type: " + type);
    }

    private ObjectNode buildMessageSchema(ProtoModel.ProtoMessage msg,
                                          Map<String, ObjectNode> defs) {
        ObjectNode schema = obj();
        schema.put("type", "object");
        if (!msg.description.isEmpty()) schema.put("description", msg.description);
        schema.put("additionalProperties", false);
        ObjectNode props = schema.putObject("properties");
        for (ProtoModel.ProtoField field : msg.fields) {
            props.set(field.camelName, fieldToSchema(field, msg.qualifiedName, defs));
        }
        return schema;
    }

    private void collectDefs(ProtoModel.ProtoMessage msg,
                             Map<String, ObjectNode> defs,
                             Set<String> visited) {
        if (!visited.add(msg.qualifiedName)) return;
        for (ProtoModel.ProtoField field : msg.fields) {
            String typeToResolve = field.isMap ? field.mapValueType : field.type;
            String resolved = resolveTypeName(typeToResolve, msg.qualifiedName, messageIndex);
            if (resolved != null) {
                String defKey = resolved.replace('.', '_');
                if (!defs.containsKey(defKey)) {
                    defs.put(defKey, obj()); // placeholder
                    defs.put(defKey, buildMessageSchema(messageIndex.get(resolved), defs));
                    collectDefs(messageIndex.get(resolved), defs, visited);
                }
            }
        }
    }

    private <V> String resolveTypeName(String typeName, String contextQualified,
                                       Map<String, V> index) {
        List<String> parts = new ArrayList<>(Arrays.asList(contextQualified.split("\\.")));
        while (!parts.isEmpty()) {
            String candidate = String.join(".", parts) + "." + typeName;
            if (index.containsKey(candidate)) return candidate;
            parts.removeLast();
        }
        return index.containsKey(typeName) ? typeName : null;
    }

    private void indexMessages(List<ProtoModel.ProtoMessage> messages) {
        for (ProtoModel.ProtoMessage msg : messages) {
            messageIndex.put(msg.qualifiedName, msg);
            indexMessages(msg.nestedMessages);
            indexEnums(msg.nestedEnums);
        }
    }

    private void indexEnums(List<ProtoModel.ProtoEnum> enums) {
        for (ProtoModel.ProtoEnum e : enums) {
            enumIndex.put(e.qualifiedName, e);
        }
    }

    private ObjectNode obj() {
        return mapper.createObjectNode();
    }

    private ObjectNode scalar(String type) {
        return obj().put("type", type);
    }

    private ProtoModel.ProtoMessage requireMessage(String qualifiedName) {
        ProtoModel.ProtoMessage msg = messageIndex.get(qualifiedName);
        if (msg == null) throw new IllegalStateException("Message not found in proto: " + qualifiedName);
        return msg;
    }
}
