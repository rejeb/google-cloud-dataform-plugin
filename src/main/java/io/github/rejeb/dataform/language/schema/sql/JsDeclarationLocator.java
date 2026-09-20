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
package io.github.rejeb.dataform.language.schema.sql;

import com.intellij.lang.javascript.psi.JSCallExpression;
import com.intellij.lang.javascript.psi.JSExpression;
import com.intellij.lang.javascript.psi.JSFile;
import com.intellij.lang.javascript.psi.JSLiteralExpression;
import com.intellij.lang.javascript.psi.JSObjectLiteralExpression;
import com.intellij.lang.javascript.psi.JSProperty;
import com.intellij.lang.javascript.psi.JSReferenceExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import io.github.rejeb.dataform.language.psi.SqlxConfigBlock;
import io.github.rejeb.dataform.language.psi.SqlxFile;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Locates the declaration of a source table: the {@code declare()} call naming it in a JavaScript
 * file, or the {@code config} block of a SQLX file of type {@code declaration}.
 *
 * <p>A source is declared rather than built: no query names its columns, so the closest thing to
 * a declaration of one of them is the call declaring the table it belongs to. One file may declare
 * several sources, so the call is matched on its {@code name} property, and what is pointed at is
 * that property's value — the line a reader looks for.</p>
 */
public final class JsDeclarationLocator {

    private static final String DECLARE = "declare";
    private static final String NAME = "name";
    private static final String TYPE = "type";
    private static final String DECLARATION = "declaration";

    private JsDeclarationLocator() {
    }

    /**
     * The value of the {@code name} property of the {@code declare()} call declaring
     * {@code tableName} in {@code file}, or {@code null} when the file declares no such table.
     */
    public static @Nullable PsiElement findDeclaration(@NotNull PsiFile file,
                                                       @NotNull String tableName) {
        if (file instanceof SqlxFile sqlxFile) return sqlxDeclaration(sqlxFile, tableName);
        if (!(file instanceof JSFile)) return null;
        for (JSCallExpression call : PsiTreeUtil.findChildrenOfType(file, JSCallExpression.class)) {
            if (!isDeclareCall(call)) continue;
            JSExpression name = declaredName(call);
            if (name != null && tableName.equals(stringValueOf(name))) return name;
        }
        return null;
    }

    /**
     * The {@code config} block of a SQLX declaration file, when it declares {@code tableName}: a
     * declaration written as SQLX has no query, so its config block is the whole of it. The name
     * defaults to the file name when the config does not spell it.
     */
    private static @Nullable PsiElement sqlxDeclaration(@NotNull SqlxFile file, @NotNull String tableName) {
        SqlxConfigBlock config = PsiTreeUtil.findChildOfType(file, SqlxConfigBlock.class);
        if (config == null) return null;
        InjectedLanguageManager manager = InjectedLanguageManager.getInstance(file.getProject());
        List<Pair<PsiElement, TextRange>> injected = manager.getInjectedPsiFiles(config);
        if (injected == null) return null;
        for (Pair<PsiElement, TextRange> pair : injected) {
            JSObjectLiteralExpression literal =
                    PsiTreeUtil.findChildOfType(pair.getFirst(), JSObjectLiteralExpression.class);
            if (literal == null) continue;
            JSProperty type = literal.findProperty(TYPE);
            if (type == null || type.getValue() == null || !DECLARATION.equals(stringValueOf(type.getValue()))) {
                continue;
            }
            JSProperty name = literal.findProperty(NAME);
            String declared = name != null && name.getValue() != null
                    ? stringValueOf(name.getValue())
                    : file.getVirtualFile() != null ? file.getVirtualFile().getNameWithoutExtension() : null;
            if (tableName.equals(declared)) return config;
        }
        return null;
    }

    private static boolean isDeclareCall(@NotNull JSCallExpression call) {
        return call.getMethodExpression() instanceof JSReferenceExpression method
                && method.getQualifier() == null
                && DECLARE.equals(method.getReferenceName());
    }

    private static @Nullable JSExpression declaredName(@NotNull JSCallExpression call) {
        JSExpression[] arguments = call.getArguments();
        if (arguments.length == 0 || !(arguments[0] instanceof JSObjectLiteralExpression config)) {
            return null;
        }
        JSProperty property = config.findProperty(NAME);
        return property == null ? null : property.getValue();
    }

    private static @Nullable String stringValueOf(@NotNull JSExpression expression) {
        return expression instanceof JSLiteralExpression literal && literal.isQuotedLiteral()
                ? literal.getStringValue()
                : null;
    }
}
