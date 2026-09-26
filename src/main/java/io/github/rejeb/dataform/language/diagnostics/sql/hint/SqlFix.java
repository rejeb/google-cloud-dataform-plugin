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
package io.github.rejeb.dataform.language.diagnostics.sql.hint;

import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;

/**
 * A fix offered for a SQL error: the text to write over a range of the SQLX file.
 *
 * @param label       what the fix is called in the Alt+Enter menu
 * @param range       the host range replaced, empty for an insertion
 * @param replacement the text written in its place, empty for a deletion
 */
public record SqlFix(@NotNull String label, @NotNull TextRange range, @NotNull String replacement) {
}
