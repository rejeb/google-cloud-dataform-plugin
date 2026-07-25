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

import com.intellij.psi.PsiFile;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiTreeUtil;
import io.github.rejeb.dataform.language.psi.SqlxSqlBlock;
import io.github.rejeb.dataform.language.schema.sql.model.ColumnInfo;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts BigQuery scripting variables declared with {@code DECLARE} from the operation blocks of
 * a SQLX file, so that references to them resolve in the separately injected main query.
 */
public final class DataformDeclaredVariablesScanner {

    private static final Pattern DECLARE =
            Pattern.compile("(?is)\\bDECLARE\\b\\s+(.+?);");
    private static final Pattern NAMES_AND_TYPE =
            Pattern.compile("(?s)^([A-Za-z_][\\w]*(?:\\s*,\\s*[A-Za-z_][\\w]*)*)\\s+(.+)$");
    private static final Pattern DEFAULT_CLAUSE =
            Pattern.compile("(?is)\\bDEFAULT\\b");
    private static final Pattern STRUCT_TYPE =
            Pattern.compile("(?is)^STRUCT\\s*<(.*)>$");

    private DataformDeclaredVariablesScanner() {
    }

    /**
     * Returns the declared variables of the given SQLX file, keyed by lower-cased variable name.
     */
    @NotNull
    public static Map<String, ColumnInfo> scan(@NotNull PsiFile sqlxFile) {
        return CachedValuesManager.getCachedValue(sqlxFile, () ->
                CachedValueProvider.Result.create(parse(sqlxFile), sqlxFile));
    }

    private static Map<String, ColumnInfo> parse(@NotNull PsiFile sqlxFile) {
        Map<String, ColumnInfo> variables = new LinkedHashMap<>();
        for (SqlxSqlBlock block : PsiTreeUtil.findChildrenOfType(sqlxFile, SqlxSqlBlock.class)) {
            String text = block.getText();
            if (text == null || text.isEmpty()) {
                continue;
            }
            Matcher matcher = DECLARE.matcher(text);
            while (matcher.find()) {
                parseDeclaration(matcher.group(1), variables);
            }
        }
        return variables.isEmpty() ? Collections.emptyMap() : Map.copyOf(variables);
    }

    private static void parseDeclaration(@NotNull String body, @NotNull Map<String, ColumnInfo> out) {
        String declaration = body.trim();
        Matcher defaultMatcher = DEFAULT_CLAUSE.matcher(declaration);
        if (defaultMatcher.find()) {
            declaration = declaration.substring(0, defaultMatcher.start()).trim();
        }

        Matcher namesAndType = NAMES_AND_TYPE.matcher(declaration);
        if (!namesAndType.matches()) {
            return;
        }
        String namesPart = namesAndType.group(1);
        String type = namesAndType.group(2).trim();

        for (String rawName : namesPart.split(",")) {
            String name = rawName.trim();
            if (name.isEmpty()) {
                continue;
            }
            out.put(name.toLowerCase(Locale.ROOT), buildColumnInfo(name, type));
        }
    }

    @NotNull
    private static ColumnInfo buildColumnInfo(@NotNull String name, @NotNull String type) {
        Matcher struct = STRUCT_TYPE.matcher(type.trim());
        if (struct.matches()) {
            List<ColumnInfo> fields = new ArrayList<>();
            for (String field : splitTopLevel(struct.group(1))) {
                ColumnInfo parsed = parseStructField(field.trim());
                if (parsed != null) {
                    fields.add(parsed);
                }
            }
            return new ColumnInfo(name, "STRUCT", "NULLABLE", null, fields);
        }
        String mode = type.regionMatches(true, 0, "ARRAY", 0, 5) ? "REPEATED" : "NULLABLE";
        return new ColumnInfo(name, type, mode, null);
    }

    private static ColumnInfo parseStructField(@NotNull String field) {
        int split = firstTopLevelSpace(field);
        if (split <= 0) {
            return null;
        }
        String fieldName = field.substring(0, split).trim();
        String fieldType = field.substring(split + 1).trim();
        if (fieldName.isEmpty() || fieldType.isEmpty()) {
            return null;
        }
        return buildColumnInfo(fieldName, fieldType);
    }

    private static int firstTopLevelSpace(@NotNull String field) {
        int depth = 0;
        for (int i = 0; i < field.length(); i++) {
            char c = field.charAt(i);
            if (c == '<') {
                depth++;
            } else if (c == '>') {
                depth--;
            } else if (Character.isWhitespace(c) && depth == 0) {
                return i;
            }
        }
        return -1;
    }

    @NotNull
    private static List<String> splitTopLevel(@NotNull String inner) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < inner.length(); i++) {
            char c = inner.charAt(i);
            if (c == '<') {
                depth++;
            } else if (c == '>') {
                depth--;
            } else if (c == ',' && depth == 0) {
                parts.add(inner.substring(start, i));
                start = i + 1;
            }
        }
        if (start < inner.length()) {
            parts.add(inner.substring(start));
        }
        return parts;
    }
}
