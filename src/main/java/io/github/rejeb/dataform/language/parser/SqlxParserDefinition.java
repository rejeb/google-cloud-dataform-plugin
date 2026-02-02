/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
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
package io.github.rejeb.dataform.language.parser;

import com.intellij.json.JsonElementTypes;
import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import io.github.rejeb.dataform.language.SqlxLanguage;
import io.github.rejeb.dataform.language.lexer.SqlxLexerAdapter;
import io.github.rejeb.dataform.language.psi.*;
import org.jetbrains.annotations.NotNull;

public class SqlxParserDefinition implements ParserDefinition {

    private static final TokenSet WHITE_SPACES = TokenSet.create(TokenType.WHITE_SPACE);
    private static final TokenSet COMMENTS = TokenSet.create(SharedTokenTypes.COMMENT);

    private static final IFileElementType FILE = new IFileElementType(SqlxLanguage.INSTANCE);

    @NotNull
    @Override
    public Lexer createLexer(Project project) {
        return new SqlxLexerAdapter();
    }

    @Override
    public @NotNull PsiParser createParser(Project project) {
        return new SqlxParser();
    }

    @Override
    public @NotNull IFileElementType getFileNodeType() {
        return FILE;
    }

    @NotNull
    @Override
    public TokenSet getWhitespaceTokens() {
        return WHITE_SPACES;
    }

    @NotNull
    @Override
    public TokenSet getCommentTokens() {
        return COMMENTS;
    }

    @NotNull
    @Override
    public TokenSet getStringLiteralElements() {
        return TokenSet.create(JsonElementTypes.STRING_LITERAL);
    }

    @NotNull
    @Override
    public PsiElement createElement(ASTNode node) {
        IElementType type = node.getElementType();

        if (type == SqlxElementTypes.SQL_BLOCK) {
            return new SqlxSqlBlock(node);
        }
        if (type == SqlxElementTypes.CONFIG_BLOCK) {
            return new SqlxConfigBlock(node);
        }
        if (type == SqlxElementTypes.JS_BLOCK) {
            return new SqlxJsBlock(node);
        }

        if (type == SqlxElementTypes.REFERENCE_EXPRESSION_ELEMENT) {
            return new SqlxConfigRefExpression(node);
        }

        if (type == SqlxElementTypes.TEMPLATE_EXPRESSION_ELEMENT || type == SqlxElementTypes.JS_LITTERAL_ELEMENT) {
            return new SqlxJsLitteralExpression(node);
        }
        return new SqlxPsiElement(node);
    }

    @Override
    public @NotNull PsiFile createFile(@NotNull FileViewProvider viewProvider) {
        return new SqlxFile(viewProvider);
    }
}