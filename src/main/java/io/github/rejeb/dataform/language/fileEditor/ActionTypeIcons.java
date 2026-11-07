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
package io.github.rejeb.dataform.language.fileEditor;

import io.github.rejeb.dataform.language.DataformIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;

/**
 * The icon in front of the name of an action in the query and schema previews, chosen from its
 * Dataform type.
 */
final class ActionTypeIcons {

    private ActionTypeIcons() {
    }

    @NotNull
    static Icon of(@Nullable String type) {
        if (type == null) return DataformIcons.ACTION_TABLE;
        return switch (type) {
            case "assertion" -> DataformIcons.ACTION_ASSERTION;
            case "operation", "operations" -> DataformIcons.ACTION_OPERATIONS;
            case "declaration" -> DataformIcons.ACTION_DECLARATION;
            default -> DataformIcons.ACTION_TABLE;
        };
    }
}
