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
package io.github.rejeb.dataform.language.formatting;

import com.intellij.formatting.service.AsyncDocumentFormattingService;
import com.intellij.formatting.service.AsyncFormattingRequest;
import com.intellij.lang.javascript.JavascriptLanguage;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiFile;
import com.intellij.sql.psi.SqlLanguage;
import io.github.rejeb.dataform.language.SqlxLanguage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.Set;

public class SqlxFormattingService extends AsyncDocumentFormattingService {

    @Override
    protected @Nullable FormattingTask createFormattingTask(@NotNull AsyncFormattingRequest formattingRequest) {

        return new SqlxFormattingTask(formattingRequest);
    }

    @Override
    protected @NotNull String getNotificationGroupId() {
        return "Dataform.Notifications";
    }

    @Override
    protected @NotNull @NlsSafe String getName() {
        return "SQLX formatting service";
    }

    @Override
    public @NotNull Set<Feature> getFeatures() {
        return Set.of(Feature.FORMAT_FRAGMENTS);
    }

    @Override
    protected Duration getTimeout() {
        return Duration.ofSeconds(5);
    }

    @Override
    protected boolean needToUpdate() {
        return false;
    }

    @Override
    public boolean canFormat(@NotNull PsiFile file) {
        if (file.getLanguage().is(SqlxLanguage.INSTANCE)) {
            return true;
        } else if (file.getLanguage().is(JavascriptLanguage.INSTANCE) || file.getLanguage().isKindOf(SqlLanguage.INSTANCE)) {
            return file.getContext() != null &&
                    file.getContext().getContainingFile() != null &&
                    file.getContext().getContainingFile().getLanguage().is(SqlxLanguage.INSTANCE);
        } else {
            return false;
        }
    }
}
