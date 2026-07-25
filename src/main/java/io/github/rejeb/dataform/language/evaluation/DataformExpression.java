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
package io.github.rejeb.dataform.language.evaluation;

import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;

/**
 * A single Dataform expression occurrence collected from a file.
 *
 * @param source    the JavaScript source to evaluate, without the surrounding <code>${</code> and <code>}</code>
 * @param hostText  the exact text of the element that will be folded
 * @param hostRange the range of that element in the document it belongs to
 * @param kind      the surface the occurrence was collected from
 */
public record DataformExpression(@NotNull String source,
                                 @NotNull String hostText,
                                 @NotNull TextRange hostRange,
                                 @NotNull DataformExpressionKind kind) {
}
