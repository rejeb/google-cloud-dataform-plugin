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
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.psi.tree.IElementType;
import com.intellij.sql.dialects.mongo.js.JSElementTypes;
import io.github.rejeb.dataform.language.psi.SharedTokenTypes;
import io.github.rejeb.dataform.language.psi.SqlxElementTypes;
import org.jetbrains.annotations.NotNull;

public class SqlxParser implements PsiParser {

    @NotNull
    @Override
    public ASTNode parse(@NotNull IElementType root, @NotNull PsiBuilder builder) {
        PsiBuilder.Marker rootMarker = builder.mark();

        while (!builder.eof()) {
            IElementType tokenType = builder.getTokenType();

            if (tokenType == SharedTokenTypes.CONFIG_KEYWORD) {
                markElement(builder, SharedTokenTypes.CONFIG_KEYWORD);
                parseConfigBlock(builder);
            } else if (tokenType == SharedTokenTypes.JS_KEYWORD) {
                parseJsBlock(builder);
            } else {
                parseSqlBlock(builder);
            }
        }

        rootMarker.done(root);
        return builder.getTreeBuilt();
    }

    private void parseConfigBlock(PsiBuilder builder) {
        PsiBuilder.Marker marker = builder.mark();

        if (builder.getTokenType() == JsonElementTypes.L_CURLY) {
            parseConfigObject(builder);
        }

        marker.done(SqlxElementTypes.CONFIG_BLOCK);
    }

    private void parseConfigObject(PsiBuilder builder) {
        PsiBuilder.Marker objectMarker = builder.mark();
        builder.advanceLexer();

        while (!builder.eof() && builder.getTokenType() != JsonElementTypes.R_CURLY) {
            parseConfigProperty(builder);

            if (builder.getTokenType() == JsonElementTypes.COMMA) {
                builder.advanceLexer();
            }
        }

        if (builder.getTokenType() == JsonElementTypes.R_CURLY) {
            builder.advanceLexer();
        }

        objectMarker.done(SqlxElementTypes.CONFIG_OBJECT);
    }

    private void parseConfigProperty(PsiBuilder builder) {
        PsiBuilder.Marker propertyMarker = builder.mark();

        IElementType keyType = builder.getTokenType();
        if (keyType == JsonElementTypes.DOUBLE_QUOTED_STRING ||
                keyType == JsonElementTypes.SINGLE_QUOTED_STRING ||
                keyType == JsonElementTypes.IDENTIFIER) {
            builder.advanceLexer();
        } else {
            propertyMarker.error("Property key expected");
            if (!builder.eof()) {
                builder.advanceLexer();
            }
            propertyMarker.drop();
            return;
        }

        if (builder.getTokenType() == JsonElementTypes.COLON) {
            builder.advanceLexer();
            parseConfigValue(builder);
        } else {
            propertyMarker.error("':' expected");
            if (!builder.eof()) {
                builder.advanceLexer();
            }
            propertyMarker.drop();
            return;
        }

        propertyMarker.done(SqlxElementTypes.CONFIG_PROPERTY);
    }


    private void parseConfigValue(PsiBuilder builder) {

        if (builder.getTokenType() == JsonElementTypes.L_CURLY) {
            parseConfigObject(builder);
        } else if (builder.getTokenType() == JsonElementTypes.L_BRACKET) {
            parseConfigArray(builder);
        } else if (builder.getTokenType() == JsonElementTypes.DOUBLE_QUOTED_STRING ||
                builder.getTokenType() == JsonElementTypes.SINGLE_QUOTED_STRING) {
            parseStringValue(builder);
        } else if (builder.getTokenType() == JSElementTypes.STRING_TEMPLATE_EXPRESSION) {
            parseBacktickContent(builder);
        } else if (builder.getTokenType() == JsonElementTypes.NUMBER_LITERAL) {
            markElement(builder, SqlxElementTypes.CONFIG_NUMBER_VALUE);
        } else if (builder.getTokenType() == JsonElementTypes.BOOLEAN_LITERAL) {
            markElement(builder, SqlxElementTypes.CONFIG_BOOLEAN_VALUE);
        } else if (builder.getTokenType() == JsonElementTypes.NULL) {
            markElement(builder, SqlxElementTypes.CONFIG_NULL_VALUE);
        } else if (builder.getTokenType() == SharedTokenTypes.TEMPLATE_EXPRESSION) {
            markElement(builder, SqlxElementTypes.TEMPLATE_EXPRESSION_ELEMENT);
        } else if (builder.getTokenType() == SharedTokenTypes.JS_LITTERAL) {
            markElement(builder, SqlxElementTypes.JS_LITTERAL_ELEMENT);
        } else if (builder.getTokenType() == SharedTokenTypes.REFERENCE_EXPRESSION) {
            markElement(builder, SqlxElementTypes.REFERENCE_EXPRESSION_ELEMENT);
        } else {
            if (!builder.eof()) {
                builder.advanceLexer();
            }
        }

    }


    private void parseBacktickContent(PsiBuilder builder) {
        while (!builder.eof()) {
            IElementType tokenType = builder.getTokenType();

            if (tokenType == JSElementTypes.STRING_TEMPLATE_EXPRESSION) {
                builder.advanceLexer();
            } else {
                break;
            }
        }
    }

    private void parseStringValue(PsiBuilder builder) {
        PsiBuilder.Marker stringMarker = builder.mark();

        while (!builder.eof()) {
            IElementType tokenType = builder.getTokenType();

            if (tokenType == JsonElementTypes.DOUBLE_QUOTED_STRING ||
                    tokenType == JsonElementTypes.SINGLE_QUOTED_STRING) {
                builder.advanceLexer();
            } else {
                break;
            }
        }

        stringMarker.done(SqlxElementTypes.CONFIG_STRING_VALUE);
    }

    private void parseConfigArray(PsiBuilder builder) {
        PsiBuilder.Marker arrayMarker = builder.mark();
        builder.advanceLexer();

        while (!builder.eof() && builder.getTokenType() != JsonElementTypes.R_BRACKET) {
            parseConfigValue(builder);

            if (builder.getTokenType() == JsonElementTypes.COMMA) {
                builder.advanceLexer();
            }
        }
        if (builder.getTokenType() == JsonElementTypes.R_BRACKET) {
            builder.advanceLexer();
        }
        arrayMarker.done(SqlxElementTypes.CONFIG_ARRAY);
    }

    private void parseJsBlock(PsiBuilder builder) {
        PsiBuilder.Marker marker = builder.mark();
        while (!builder.eof()) {
            builder.advanceLexer();
            IElementType tokenType = builder.getTokenType();

            if (tokenType != SharedTokenTypes.JS_CONTENT &&
                    tokenType != SharedTokenTypes.JS_KEYWORD) {
                break;
            }
        }

        marker.done(SqlxElementTypes.JS_BLOCK);
    }

    private void parseSqlBlock(PsiBuilder builder) {
        PsiBuilder.Marker sqlMarker = builder.mark();

        while (!builder.eof()) {
            IElementType tokenType = builder.getTokenType();

            if (tokenType == SharedTokenTypes.CONFIG_KEYWORD ||
                    tokenType == SharedTokenTypes.JS_KEYWORD) {
                break;
            }
            if (tokenType == SharedTokenTypes.TEMPLATE_EXPRESSION) {
                markElement(builder, SqlxElementTypes.TEMPLATE_EXPRESSION_ELEMENT);
            } else {
                builder.advanceLexer();
            }
        }

        sqlMarker.done(SqlxElementTypes.SQL_BLOCK);
    }

    private void markElement(PsiBuilder builder, IElementType elementType) {
        PsiBuilder.Marker templateMarker = builder.mark();
        builder.advanceLexer();
        templateMarker.done(elementType);
    }
}
