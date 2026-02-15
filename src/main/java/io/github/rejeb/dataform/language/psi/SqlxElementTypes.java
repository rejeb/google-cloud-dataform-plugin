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
package io.github.rejeb.dataform.language.psi;

import com.intellij.psi.tree.IElementType;
import io.github.rejeb.dataform.language.SqlxLanguage;

public interface SqlxElementTypes {
    IElementType CONFIG_BLOCK = new IElementType("CONFIG_BLOCK", SqlxLanguage.INSTANCE);
    IElementType JS_BLOCK = new IElementType("JS_BLOCK", SqlxLanguage.INSTANCE);
    IElementType SQL_BLOCK = new IElementType("SQL_BLOCK", SqlxLanguage.INSTANCE);
    IElementType TEMPLATE_EXPRESSION_ELEMENT = new IElementType("TEMPLATE_EXPRESSION_ELEMENT", SqlxLanguage.INSTANCE);
    IElementType JS_LITTERAL_ELEMENT = new IElementType("JS_LITTERAL_ELEMENT", SqlxLanguage.INSTANCE);
    IElementType CONFIG_OBJECT = new IElementType("CONFIG_OBJECT", SqlxLanguage.INSTANCE);
    IElementType CONFIG_ARRAY = new IElementType("CONFIG_ARRAY", SqlxLanguage.INSTANCE);
    IElementType CONFIG_PROPERTY = new IElementType("CONFIG_PROPERTY", SqlxLanguage.INSTANCE);
    IElementType CONFIG_STRING_VALUE = new IElementType("CONFIG_STRING_VALUE", SqlxLanguage.INSTANCE);
    IElementType CONFIG_NUMBER_VALUE = new IElementType("CONFIG_NUMBER_VALUE", SqlxLanguage.INSTANCE);
    IElementType CONFIG_BOOLEAN_VALUE = new IElementType("CONFIG_BOOLEAN_VALUE", SqlxLanguage.INSTANCE);
    IElementType CONFIG_NULL_VALUE = new IElementType("CONFIG_NULL_VALUE", SqlxLanguage.INSTANCE);
}
