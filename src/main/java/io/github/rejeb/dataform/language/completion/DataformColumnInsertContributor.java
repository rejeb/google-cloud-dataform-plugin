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
package io.github.rejeb.dataform.language.completion;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementDecorator;
import com.intellij.database.model.DasObject;
import com.intellij.database.symbols.DasSymbol;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.ResolveResult;
import com.intellij.sql.completion.SqlQualifiedResolveResult;
import io.github.rejeb.dataform.language.schema.sql.model.DataformDasColumn;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Writes a Dataform column under its bare name when it is picked from the completion popup.
 *
 * <p>The SQL plugin qualifies what it inserts with the name of the object's parent whenever
 * <em>Settings | Editor | General | Code Completion | SQL | Qualify object in</em> lists basic
 * completion, which it does by default. For a column of an action that is wrong twice over: the
 * query already names the table in its {@code FROM}, and the name it would be qualified with is the
 * name of a Dataform action, not of anything the query can reference.</p>
 *
 * <p>Only the columns of this plugin are touched, and only what they write: everything else the SQL
 * completion offers passes through untouched, qualification included.</p>
 *
 * <p>A column is handed on as the result the SQL completion produced, with the element swapped. That
 * result carries the prefix the contributor behind it matched on and the order it sorts by; offering
 * the element as one of this contributor's own would match it against another prefix, and a column
 * offered after a qualifier the SQL completion narrowed the prefix on would drop out of the popup
 * altogether.</p>
 */
public class DataformColumnInsertContributor extends CompletionContributor {

    @Override
    public void fillCompletionVariants(@NotNull CompletionParameters parameters,
                                       @NotNull CompletionResultSet result) {
        if (!isInSqlxFile(parameters.getPosition())) {
            super.fillCompletionVariants(parameters, result);
            return;
        }
        result.runRemainingContributors(parameters, completionResult -> {
            DataformDasColumn column = columnOf(completionResult.getLookupElement());
            if (column == null) {
                result.passResult(completionResult);
                return;
            }
            result.passResult(completionResult.withLookupElement(
                    unqualified(completionResult.getLookupElement(), column.getName())));
        });
    }

    /** The element writes the name and nothing else, whatever the delegate would have written. */
    private static @NotNull LookupElement unqualified(@NotNull LookupElement element,
                                                      @NotNull String name) {
        return LookupElementDecorator.withInsertHandler(element,
                (InsertionContext context, LookupElementDecorator<LookupElement> item) ->
                        context.getDocument().replaceString(context.getStartOffset(),
                                context.getTailOffset(), name));
    }

    /**
     * The Dataform column an offered element stands for, or {@code null} when it stands for none.
     *
     * <p>The SQL completion offers a column as the result of resolving it, which carries the
     * qualification it means to write. The column itself is at the end of that chain.</p>
     */
    private static @Nullable DataformDasColumn columnOf(@NotNull LookupElement element) {
        if (element.getPsiElement() instanceof DataformDasColumn column) return column;
        return columnOf(element.getObject());
    }

    private static @Nullable DataformDasColumn columnOf(@Nullable Object object) {
        if (object instanceof DataformDasColumn column) return column;
        if (object instanceof ResolveResult result) {
            if (result.getElement() instanceof DataformDasColumn column) return column;
        }
        if (object instanceof SqlQualifiedResolveResult qualified) {
            return columnOf(qualified.getTargetSymbol());
        }
        if (object instanceof DasSymbol symbol) {
            DasObject target = symbol.getDasObject();
            if (target instanceof DataformDasColumn column) return column;
        }
        return null;
    }

    private static boolean isInSqlxFile(@NotNull PsiElement position) {
        PsiFile containing = position.getContainingFile();
        if (containing == null) return false;
        PsiFile host = InjectedLanguageManager.getInstance(position.getProject())
                .getTopLevelFile(containing);
        return host != null && host.getName().endsWith(".sqlx");
    }
}
