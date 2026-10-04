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
package io.github.rejeb.dataform.language.diagnostics.sql.bigquery;

import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Turns the position BigQuery reports an error at into an offset of the query that was sent.
 */
public final class QueryPositions {

    static final int TAB_WIDTH = 8;

    private QueryPositions() {
    }

    /**
     * The offset of a one-based line and column of a query, or {@code -1} when the query has no
     * such line. Lines end at a line feed, a carriage return, or both. The column is counted as
     * the BigQuery analyzer counts it, with a tab reaching the next multiple of eight; a column
     * past the end of its line is the end of that line.
     */
    public static int offsetOf(@NotNull CharSequence query, int line, int column) {
        if (line < 1 || column < 1) return -1;
        int at = 0;
        for (int current = 1; current < line; current++) {
            int end = CharArrayUtil.shiftForwardUntil(query, at, "\n\r");
            if (end >= query.length()) return -1;
            boolean crlf = query.charAt(end) == '\r' && end + 1 < query.length() && query.charAt(end + 1) == '\n';
            at = end + (crlf ? 2 : 1);
        }
        int lineEnd = CharArrayUtil.shiftForwardUntil(query, at, "\n\r");
        int visual = 1;
        while (at < lineEnd) {
            int next = query.charAt(at) == '\t' ? visual + TAB_WIDTH - (visual - 1) % TAB_WIDTH : visual + 1;
            if (next > column) break;
            visual = next;
            at++;
        }
        return at;
    }
}
