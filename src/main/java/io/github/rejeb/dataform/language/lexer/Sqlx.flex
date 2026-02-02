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
package io.github.rejeb.dataform.language.lexer;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import io.github.rejeb.dataform.language.psi.SharedTokenTypes;

%%

%class SqlxLexer
%implements FlexLexer
%unicode
%function advance
%type IElementType
%eof{  return;
%eof}

%{
  private int braceDepth = 0;
%}

%xstate CONFIG_BLOCK
%xstate JS_BLOCK
%xstate SQL_CONTENT

WHITE_SPACE = [ \t\n\r]+
Identifier = [a-zA-Z_][a-zA-Z0-9_]*

CONFIG_KEYWORD="config"
JS_KEYWORD="js"


LBRACE="{"
RBRACE="}"

%%

<YYINITIAL> {
  {CONFIG_KEYWORD}  {WHITE_SPACE}*  { yybegin(CONFIG_BLOCK);
          return SharedTokenTypes.CONFIG_KEYWORD;
        }

  {JS_KEYWORD} {WHITE_SPACE}*  {yybegin(JS_BLOCK);
                          return SharedTokenTypes.JS_KEYWORD;  }
  {WHITE_SPACE}      { return TokenType.WHITE_SPACE; }
  [^]               { yybegin(SQL_CONTENT);
                      yypushback(yylength());
      }
}

<CONFIG_BLOCK> {
  {LBRACE}          { braceDepth++; }
  {RBRACE}          { braceDepth--;
                 if (braceDepth == 0) {
                   yybegin(YYINITIAL);
                   return SharedTokenTypes.CONFIG_CONTENT;
                 } }
  [^{}]+       { /* accumule mais ne retourne rien */ }
}

<JS_BLOCK> {
  "{"          { braceDepth++; }
  "}"          { braceDepth--;
                 if (braceDepth == 0) {
                   yybegin(YYINITIAL);
                   return SharedTokenTypes.JS_CONTENT;
                 } }
  [^{}]+       { /* accumule mais ne retourne rien */ }
}


<SQL_CONTENT> {

    {JS_KEYWORD} {
        yybegin(JS_BLOCK);
        yypushback(yylength());
    }


    "${"[^}]*"}" {
        return SharedTokenTypes.TEMPLATE_EXPRESSION;
    }


    [^$]+ {
        return SharedTokenTypes.SQL_CONTENT;
    }

    "$" {
        return SharedTokenTypes.SQL_CONTENT;
    }
}
