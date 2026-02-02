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

public interface SharedTokenTypes {
    IElementType CONFIG_CONTENT = new IElementType("CONFIG_CONTENT", SqlxLanguage.INSTANCE);
    IElementType JS_CONTENT = new IElementType("JS_CONTENT", SqlxLanguage.INSTANCE);
    IElementType SQL_CONTENT = new IElementType("SQL_CONTENT", SqlxLanguage.INSTANCE);
    IElementType TEMPLATE_EXPRESSION = new IElementType("TEMPLATE_EXPRESSION", SqlxLanguage.INSTANCE);
    IElementType REFERENCE_EXPRESSION = new IElementType("REFERENCE_EXPRESSION", SqlxLanguage.INSTANCE);
    IElementType JS_LITTERAL = new IElementType("JS_LITTERAL", SqlxLanguage.INSTANCE);
    IElementType CONFIG_KEYWORD = new IElementType("CONFIG_KEYWORD", SqlxLanguage.INSTANCE);
    IElementType JS_KEYWORD = new IElementType("JS_KEYWORD", SqlxLanguage.INSTANCE);
    IElementType COMMENT = new IElementType("COMMENT", SqlxLanguage.INSTANCE);
}
