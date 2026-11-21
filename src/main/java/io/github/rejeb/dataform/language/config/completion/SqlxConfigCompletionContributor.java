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
package io.github.rejeb.dataform.language.config.completion;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionType;

/**
 * Completes the config block of a SQLX file: the project tags in the {@code tags} list, the columns
 * of the action wherever the config names one, the property keys of the object being edited, then
 * the values the config schema allows.
 *
 * <p>The providers run in this order and the first one that offers something stops the others. The
 * values come last: inside an object value such as {@code partitionBy: { }} they would offer the
 * shapes of the value instead of its keys.</p>
 */
public final class SqlxConfigCompletionContributor extends CompletionContributor {

    public SqlxConfigCompletionContributor() {
        extend(CompletionType.BASIC, ConfigCompletionPatterns.tagsArrayValue(), new ConfigTagProvider());
        extend(CompletionType.BASIC, ConfigCompletionPatterns.columnSlot(), new ConfigColumnProvider());
        extend(CompletionType.BASIC, ConfigCompletionPatterns.keyPosition(), new ConfigPropertyProvider());
        extend(CompletionType.BASIC, ConfigCompletionPatterns.inConfigBlock(), new ConfigValueProvider());
    }
}
