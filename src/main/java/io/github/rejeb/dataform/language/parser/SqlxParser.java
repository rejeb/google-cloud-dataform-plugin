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
package io.github.rejeb.dataform.language.parser;

import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.psi.tree.IElementType;
import io.github.rejeb.dataform.language.psi.SharedTokenTypes;
import org.jetbrains.annotations.NotNull;

public class SqlxParser implements PsiParser {

    @NotNull
    @Override
    public ASTNode parse(@NotNull IElementType root, @NotNull PsiBuilder builder) {
        PsiBuilder.Marker rootMarker = builder.mark();

        while (!builder.eof()) {
            IElementType tokenType = builder.getTokenType();

            if (tokenType == SharedTokenTypes.CONFIG_KEYWORD) {
                PsiBuilder.Marker blockMarker = builder.mark();
                markElement(builder, SharedTokenTypes.CONFIG_KEYWORD);
                markOptionalElement(builder, SharedTokenTypes.LBRACE);
                markOptionalElement(builder, SharedTokenTypes.CONFIG_CONTENT);
                markOptionalElement(builder, SharedTokenTypes.RBRACE);
                blockMarker.done(SharedTokenTypes.CONFIG_BLOCK);
            } else if (tokenType == SharedTokenTypes.JS_KEYWORD) {
                PsiBuilder.Marker blockMarker = builder.mark();
                markElement(builder, SharedTokenTypes.JS_KEYWORD);
                markOptionalElement(builder, SharedTokenTypes.LBRACE);
                markOptionalElement(builder, SharedTokenTypes.JS_CONTENT);
                markOptionalElement(builder, SharedTokenTypes.RBRACE);
                blockMarker.done(SharedTokenTypes.JS_BLOCK);
            } else if (tokenType == SharedTokenTypes.PRE_OPERATIONS_KEYWORD) {
                PsiBuilder.Marker blockMarker = builder.mark();
                markElement(builder, SharedTokenTypes.PRE_OPERATIONS_KEYWORD);
                parseOperationsBlock(builder, SharedTokenTypes.PRE_OPERATIONS_CONTENT);
                blockMarker.done(SharedTokenTypes.PRE_OPERATIONS_BLOCK);
            } else if (tokenType == SharedTokenTypes.POST_OPERATIONS_KEYWORD) {
                PsiBuilder.Marker blockMarker = builder.mark();
                markElement(builder, SharedTokenTypes.POST_OPERATIONS_KEYWORD);
                parseOperationsBlock(builder, SharedTokenTypes.POST_OPERATIONS_CONTENT);
                blockMarker.done(SharedTokenTypes.POST_OPERATIONS_BLOCK);
            } else {
                parseSqlBlock(builder);
            }
        }

        rootMarker.done(root);
        return builder.getTreeBuilt();
    }

    private void parseOperationsBlock(PsiBuilder builder, IElementType operationType) {
        markOptionalElement(builder, SharedTokenTypes.LBRACE);

        PsiBuilder.Marker sqlMarker = builder.mark();
        while (!builder.eof()) {
            IElementType tokenType = builder.getTokenType();
            if (tokenType == SharedTokenTypes.TEMPLATE_EXPRESSION) {
                markElement(builder, SharedTokenTypes.TEMPLATE_EXPRESSION);
            } else if (tokenType != operationType) {
                break;
            } else {
                builder.advanceLexer();
            }
        }
        sqlMarker.done(operationType);

        markOptionalElement(builder, SharedTokenTypes.RBRACE);
    }

    private void parseSqlBlock(PsiBuilder builder) {
        PsiBuilder.Marker sqlMarker = builder.mark();

        while (!builder.eof()) {
            IElementType tokenType = builder.getTokenType();

            if (tokenType == SharedTokenTypes.CONFIG_KEYWORD ||
                    tokenType == SharedTokenTypes.JS_KEYWORD ||
                    tokenType == SharedTokenTypes.PRE_OPERATIONS_KEYWORD ||
                    tokenType == SharedTokenTypes.POST_OPERATIONS_KEYWORD) {
                break;
            }
            if (tokenType == SharedTokenTypes.TEMPLATE_EXPRESSION) {
                markElement(builder, SharedTokenTypes.TEMPLATE_EXPRESSION);
            } else {
                builder.advanceLexer();
            }
        }

        sqlMarker.done(SharedTokenTypes.SQL_CONTENT);
    }


    private void markElement(PsiBuilder builder, IElementType elementType) {
        PsiBuilder.Marker templateMarker = builder.mark();
        builder.advanceLexer();
        templateMarker.done(elementType);
    }

    private void markOptionalElement(PsiBuilder builder, IElementType elementType) {
        if (builder.getTokenType() == elementType) {
            markElement(builder, elementType);
        }
    }
}
