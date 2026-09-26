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
package io.github.rejeb.dataform.language.diagnostics.compile;

import org.jetbrains.annotations.NotNull;

/**
 * The source line a JavaScript syntax error quotes, with the caret under the offending code.
 *
 * @param path        the file, relative to the Dataform project when that could be told
 * @param line        the line the stack gives, counted from one: a line of the file itself for a
 *                    JavaScript file, but of the JavaScript generated from it for a SQLX file
 * @param text        the quoted line
 * @param caretStart  the zero-based column of the first caret
 * @param caretLength the number of carets
 */
public record SourceSnippet(@NotNull String path, int line, @NotNull String text, int caretStart, int caretLength) {
}
