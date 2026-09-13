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
package io.github.rejeb.dataform.language.schema.sql.usages;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.UsageTargetProvider;
import io.github.rejeb.dataform.language.schema.sql.model.StructColumnPath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Offers Find Usages a field of a struct column when the caret sits on one.
 *
 * <p>Everything else is left to the platform: a target is offered only where a field is named, so a
 * column of a table, a name a query gives a value of its own, and every identifier that is neither
 * keep answering exactly as they did.</p>
 */
public final class StructFieldUsageTargetProvider implements UsageTargetProvider {

    @Override
    public UsageTarget @Nullable [] getTargets(@NotNull Editor editor, @NotNull PsiFile file) {
        Project project = file.getProject();
        ColumnWindowTarget target = ColumnWindowTarget.at(file, editor.getCaretModel().getOffset());
        if (target == null) return null;
        StructColumnPath path = target.structPath();
        if (path == null || !path.isField()) return null;
        return new UsageTarget[]{new StructFieldUsageTarget(project, path)};
    }
}
