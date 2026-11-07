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
package io.github.rejeb.dataform.language.gcp.execution.workflow.runconfig.ui;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Places the {@code [line:column]} BigQuery gives in a script job's error on the statement that
 * failed: the position is relative to the whole script, the statement shows only its own SQL.
 */
final class StatementErrorLocator {

    private static final Pattern POSITION = Pattern.compile("\\[(\\d{1,9}):(\\d{1,9})]");
    private static final Pattern POSITION_SUFFIX = Pattern.compile("\\s*at \\[\\d+:\\d+]\\s*$");

    private StatementErrorLocator() {
    }

    /**
     * Where an error stands in a statement, 1-based.
     *
     * @param line   the line in the statement
     * @param column the column on that line
     */
    record Position(int line, int column) {
    }

    /**
     * The position of an error in the statement at {@code index}, or {@code null} when the message has
     * no position, the script or the statement is unknown, or the position lies outside the statement.
     */
    @Nullable
    static Position locate(@Nullable String message, @Nullable String script,
                           @NotNull List<@Nullable String> statements, int index) {
        if (message == null || script == null || index < 0 || index >= statements.size()) return null;
        int[] scriptPosition = lastPosition(message);
        if (scriptPosition == null) return null;
        String text = StringUtil.convertLineSeparators(script);
        int start = statementStart(text, statements, index);
        if (start < 0) return null;
        String statement = StringUtil.convertLineSeparators(statements.get(index));
        int firstLine = 1 + StringUtil.countNewLines(text.subSequence(0, start));
        int lineCount = 1 + StringUtil.countNewLines(statement);
        int line = scriptPosition[0];
        if (line < firstLine || line >= firstLine + lineCount) return null;
        int statementLine = line - firstLine + 1;
        if (statementLine > 1) return new Position(statementLine, scriptPosition[1]);
        int startColumn = start - (text.lastIndexOf('\n', start - 1) + 1) + 1;
        return new Position(1, Math.max(1, scriptPosition[1] - (startColumn - 1)));
    }

    /**
     * The message without its trailing {@code at [line:column]}.
     */
    @NotNull
    static String withoutPosition(@NotNull String message) {
        return POSITION_SUFFIX.matcher(message).replaceFirst("");
    }

    private static int @Nullable [] lastPosition(@NotNull String message) {
        Matcher matcher = POSITION.matcher(message);
        int[] position = null;
        while (matcher.find()) {
            position = new int[]{Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2))};
        }
        return position;
    }

    private static int statementStart(@NotNull String script, @NotNull List<@Nullable String> statements, int index) {
        int from = 0;
        for (int i = 0; i < index; i++) {
            String statement = statements.get(i);
            if (statement == null) continue;
            String normalized = StringUtil.convertLineSeparators(statement);
            int at = script.indexOf(normalized, from);
            if (at >= 0) from = at + normalized.length();
        }
        String failed = statements.get(index);
        return failed != null ? script.indexOf(StringUtil.convertLineSeparators(failed), from) : -1;
    }
}
