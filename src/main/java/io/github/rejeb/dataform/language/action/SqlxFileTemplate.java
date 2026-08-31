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
package io.github.rejeb.dataform.language.action;

import org.jetbrains.annotations.NotNull;

public enum SqlxFileTemplate {
    TABLE("Table", "Dataform SQLX Table"),
    VIEW("View", "Dataform SQLX View"),
    INCREMENTAL("Incremental table", "Dataform SQLX Incremental Table"),
    ASSERTION("Assertion", "Dataform SQLX Assertion"),
    OPERATIONS("Operations", "Dataform SQLX Operations"),
    DECLARATION("Declaration", "Dataform SQLX Declaration");

    private final String kind;
    private final String templateName;

    SqlxFileTemplate(@NotNull String kind, @NotNull String templateName) {
        this.kind = kind;
        this.templateName = templateName;
    }

    /**
     * Returns the label shown in the kind selector of the "New SQLX File" dialog.
     */
    @NotNull
    public String getKind() {
        return kind;
    }

    /**
     * Returns the name of the internal file template backing this kind, as declared by the
     * {@code com.intellij.internalFileTemplate} extensions and by the files under
     * {@code fileTemplates/internal}.
     */
    @NotNull
    public String getTemplateName() {
        return templateName;
    }
}
