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
package io.github.rejeb.dataform.language.inspection;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.javascript.psi.JSLiteralExpression;
import com.intellij.lang.javascript.psi.JSObjectLiteralExpression;
import com.intellij.lang.javascript.psi.JSProperty;
import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import io.github.rejeb.dataform.language.completion.config.ConfigSchemaLookup;
import io.github.rejeb.dataform.language.completion.config.partition.PartitionObjectRewrite;
import io.github.rejeb.dataform.language.schema.sql.DataformActionColumns;
import io.github.rejeb.dataform.language.schema.sql.model.ColumnInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Reports a {@code partitionBy} written as an object in a SQLX config block.
 *
 * <p>Dataform declares {@code partition_by} as a string in every action of its configuration, so an
 * object describing the column, its type and a granularity, the shape other transformation tools
 * use, is not a configuration Dataform compiles. The fix writes the BigQuery partitioning
 * expression it stands for.</p>
 */
public final class PartitionByObjectInspection extends LocalInspectionTool implements DumbAware {

    private static final String PARTITION_BY = "partitionBy";
    private static final String FIELD = "field";
    private static final String DATA_TYPE = "dataType";
    private static final String GRANULARITY = "granularity";
    private static final String RANGE = "range";

    private static final String MESSAGE =
            "Dataform takes a partitioning expression here, not an object";

    @Override
    public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                                   boolean isOnTheFly) {
        return new PsiElementVisitor() {
            @Override
            public void visitElement(@NotNull PsiElement element) {
                if (element instanceof JSProperty property) {
                    inspect(property, holder);
                }
            }
        };
    }

    private static void inspect(@NotNull JSProperty property, @NotNull ProblemsHolder holder) {
        if (!PARTITION_BY.equals(property.getName())
                || !(property.getValue() instanceof JSObjectLiteralExpression object)
                || !ConfigSchemaLookup.isInConfigBlock(property)) {
            return;
        }
        Optional<String> expression = expressionOf(object);
        if (expression.isEmpty()) {
            holder.registerProblem(object, MESSAGE, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
            return;
        }
        holder.registerProblem(object, MESSAGE, ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                new PartitionByObjectFix(expression.get()));
    }

    /**
     * The expression the object stands for. The type of the column is read from the object, and
     * from the schema of the action when the object leaves it out.
     */
    @NotNull
    private static Optional<String> expressionOf(@NotNull JSObjectLiteralExpression object) {
        String field = stringOf(object, FIELD);
        if (field == null) {
            return Optional.empty();
        }
        String dataType = stringOf(object, DATA_TYPE);
        JSObjectLiteralExpression range = objectOf(object, RANGE);
        return new PartitionObjectRewrite(field,
                dataType == null ? declaredTypeOf(object, field) : dataType,
                stringOf(object, GRANULARITY),
                range == null ? null : stringOf(range, "start"),
                range == null ? null : stringOf(range, "end"),
                range == null ? null : stringOf(range, "interval")).expression();
    }

    @Nullable
    private static String declaredTypeOf(@NotNull JSObjectLiteralExpression object,
                                         @NotNull String field) {
        List<ColumnInfo> columns = DataformActionColumns.at(object);
        return DataformActionColumns.find(columns, field).map(ColumnInfo::type).orElse(null);
    }

    @Nullable
    private static String stringOf(@NotNull JSObjectLiteralExpression object, @NotNull String name) {
        JSProperty property = object.findProperty(name);
        if (property == null || !(property.getValue() instanceof JSLiteralExpression literal)) {
            return null;
        }
        Object value = literal.getValue();
        return value == null ? null : String.valueOf(value);
    }

    @Nullable
    private static JSObjectLiteralExpression objectOf(@NotNull JSObjectLiteralExpression object,
                                                      @NotNull String name) {
        JSProperty property = object.findProperty(name);
        return property != null && property.getValue() instanceof JSObjectLiteralExpression nested
                ? nested
                : null;
    }
}
