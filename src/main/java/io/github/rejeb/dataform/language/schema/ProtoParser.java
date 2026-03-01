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

import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import io.github.rejeb.dataform.setup.DataformInterpreterManager;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

@Service(Service.Level.PROJECT)
public final class ProtoParser {
    private static final Logger LOGGER = Logger.getInstance(ProtoParser.class.getName());
    private String protoContent;
    private int pos = 0;
    private String pendingComment = null;
    private final Project project;

    public ProtoParser(@NotNull Project project) {
        this.project = project;
        init();
    }

    private void init() {
        this.protoContent = getConfigProtoFilePath()
                .map(protoPath ->
                        {
                            try {
                                return Files.readString(protoPath);
                            } catch (IOException e) {
                                LOGGER.error("Unable to read file content", e);
                                return "";
                            }
                        }
                ).orElse("");

    }

    public boolean configProtoFileExists(){
        return getConfigProtoFilePath().isPresent();
    }

    private Optional<Path> getConfigProtoFilePath() {
        DataformInterpreterManager interpreterManager = this.project.getService(DataformInterpreterManager.class);
        return interpreterManager
                .dataformCorePath()
                .map(coreDir -> coreDir.toNioPath().resolve("configs.proto"))
                .filter(Files::exists);
    }

    public Optional<ProtoModel.ProtoFile> parse() {
        if (getConfigProtoFilePath().isEmpty()) {
            return Optional.empty();
        } else if (protoContent.isEmpty()) {
            init();
        }
        ProtoModel.ProtoFile file = new ProtoModel.ProtoFile();
        while (pos < protoContent.length()) {
            skipWs();
            if (pos >= protoContent.length()) break;
            collectCommentIfAny();
            skipWs();
            if (pos >= protoContent.length()) break;

            if (at("syntax") || at("package") || at("option") || at("import")) {
                consumePendingComment();
                skipStatement();
            } else if (at("message ")) {
                advance("message ");
                file.messages.add(parseMessage(""));
            } else if (at("enum ")) {
                advance("enum ");
                file.enums.add(parseEnum(""));
            } else {
                pos++;
            }
        }
        return Optional.of(file);
    }

    private ProtoModel.ProtoMessage parseMessage(String parentQualified) {
        skipWs();
        String name = readIdentifier();
        String qualified = parentQualified.isEmpty() ? name : parentQualified + "." + name;
        String desc = consumePendingComment();
        skipWs();
        expect('{');

        ProtoModel.ProtoMessage msg = new ProtoModel.ProtoMessage(name, qualified, desc);

        while (true) {
            skipWs();
            collectCommentIfAny();
            skipWs();
            if (pos >= protoContent.length()) break;
            if (protoContent.charAt(pos) == '}') {
                pos++;
                break;
            }

            if (at("message ")) {
                advance("message ");
                msg.nestedMessages.add(parseMessage(qualified));
            } else if (at("enum ")) {
                advance("enum ");
                msg.nestedEnums.add(parseEnum(qualified));
            } else if (at("oneof ")) {
                advance("oneof ");
                parseOneOf(msg);
            } else if (at("map<")) {
                ProtoModel.ProtoField f = parseMapField();
                if (f != null) msg.fields.add(f);
            } else if (at("repeated ")) {
                advance("repeated ");
                ProtoModel.ProtoField f = parseField(true, false);
                if (f != null) msg.fields.add(f);
            } else {
                ProtoModel.ProtoField f = parseField(false, false);
                if (f != null) msg.fields.add(f);
            }
        }
        return msg;
    }

    private void parseOneOf(ProtoModel.ProtoMessage msg) {
        skipWs();
        readIdentifier();
        consumePendingComment();
        skipWs();
        expect('{');
        while (true) {
            skipWs();
            collectCommentIfAny();
            skipWs();
            if (pos >= protoContent.length() || protoContent.charAt(pos) == '}') {
                if (pos < protoContent.length()) pos++;
                break;
            }
            ProtoModel.ProtoField f = parseField(false, true);
            if (f != null) msg.fields.add(f);
        }
    }

    private ProtoModel.ProtoEnum parseEnum(String parentQualified) {
        skipWs();
        String name = readIdentifier();
        String qualified = parentQualified.isEmpty() ? name : parentQualified + "." + name;
        consumePendingComment();
        skipWs();
        expect('{');

        ProtoModel.ProtoEnum e = new ProtoModel.ProtoEnum(name, qualified);
        while (true) {
            skipWs();
            collectCommentIfAny();
            skipWs();
            if (pos >= protoContent.length()) break;
            if (protoContent.charAt(pos) == '}') {
                pos++;
                break;
            }
            String valueName = readIdentifier();
            if (!valueName.isEmpty()) {
                e.values.add(valueName);
                skipToSemicolon();
            } else {
                pos++;
            }
        }
        return e;
    }

    private ProtoModel.ProtoField parseMapField() {
        advance("map<");
        String keyType = readUntil(',').trim();
        expect(',');
        skipWs();
        String valueType = readUntil('>').trim();
        expect('>');
        skipWs();
        String fieldName = readIdentifier();
        if (fieldName.isEmpty()) {
            skipToSemicolon();
            return null;
        }
        skipToSemicolon();
        String desc = consumePendingComment();
        return new ProtoModel.ProtoField(
                fieldName, toCamelCase(fieldName), valueType,
                false, true, keyType, valueType, desc, false);
    }

    private ProtoModel.ProtoField parseField(boolean repeated, boolean isOneof) {
        skipWs();
        String type = readQualifiedIdentifier();
        if (type.isEmpty()) {
            skipToSemicolon();
            return null;
        }
        skipWs();
        String fieldName = readIdentifier();
        if (fieldName.isEmpty()) {
            skipToSemicolon();
            return null;
        }
        skipToSemicolon();
        String desc = consumePendingComment();
        return new ProtoModel.ProtoField(
                fieldName, toCamelCase(fieldName), type,
                repeated, false, null, null, desc, isOneof);
    }

    private void collectCommentIfAny() {
        skipWs();
        if (!at("//")) return;
        StringBuilder sb = new StringBuilder();
        while (pos < protoContent.length() && at("//")) {
            advance("//");
            while (pos < protoContent.length() && protoContent.charAt(pos) == ' ') pos++;
            int lineEnd = protoContent.indexOf('\n', pos);
            if (lineEnd < 0) lineEnd = protoContent.length();
            String line = protoContent.substring(pos, lineEnd).trim();
            if (!line.isEmpty()) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(line);
            }
            pos = lineEnd;
            skipWs();
        }
        if (sb.length() > 0) pendingComment = sb.toString();
    }

    private String consumePendingComment() {
        String c = pendingComment != null ? pendingComment : "";
        pendingComment = null;
        return c;
    }

    private void skipWs() {
        while (pos < protoContent.length() && Character.isWhitespace(protoContent.charAt(pos))) pos++;
    }

    private void skipStatement() {
        while (pos < protoContent.length()) {
            char c = protoContent.charAt(pos++);
            if (c == ';') return;
            if (c == '{') {
                skipBlock();
                return;
            }
        }
    }

    private void skipBlock() {
        int depth = 1;
        while (pos < protoContent.length() && depth > 0) {
            char c = protoContent.charAt(pos++);
            if (c == '{') depth++;
            else if (c == '}') depth--;
        }
    }

    private void skipToSemicolon() {
        while (pos < protoContent.length() && protoContent.charAt(pos) != ';') pos++;
        if (pos < protoContent.length()) pos++;
    }

    private String readIdentifier() {
        skipWs();
        int start = pos;
        while (pos < protoContent.length() &&
                (Character.isLetterOrDigit(protoContent.charAt(pos)) || protoContent.charAt(pos) == '_')) pos++;
        return protoContent.substring(start, pos);
    }

    private String readQualifiedIdentifier() {
        String id = readIdentifier();
        while (pos < protoContent.length() && protoContent.charAt(pos) == '.') {
            pos++;
            id += "." + readIdentifier();
        }
        return id;
    }

    private String readUntil(char stop) {
        int start = pos;
        while (pos < protoContent.length() && protoContent.charAt(pos) != stop) pos++;
        return protoContent.substring(start, pos);
    }

    private void expect(char c) {
        skipWs();
        if (pos < protoContent.length() && protoContent.charAt(pos) == c) pos++;
    }

    private void advance(String s) {
        pos += s.length();
    }

    private boolean at(String s) {
        return protoContent.startsWith(s, pos);
    }

    public static String toCamelCase(String snake) {
        StringBuilder sb = new StringBuilder();
        boolean upper = false;
        for (char c : snake.toCharArray()) {
            if (c == '_') {
                upper = true;
            } else if (upper) {
                sb.append(Character.toUpperCase(c));
                upper = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
