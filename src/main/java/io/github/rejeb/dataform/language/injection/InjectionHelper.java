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
package io.github.rejeb.dataform.language.injection;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import io.github.rejeb.dataform.language.psi.SharedTokenTypes;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.List;

public class InjectionHelper {

    public static LinkedHashMap<TextRange, PsiElement> collectJsElements(
            PsiElement sqlBlock, int blockStartOffset) {
        LinkedHashMap<TextRange, PsiElement> result = new LinkedHashMap<>();
        PsiTreeUtil.processElements(sqlBlock, element -> {
            IElementType type = element.getNode().getElementType();
            if (SharedTokenTypes.TEMPLATE_EXPRESSION.equals(type)) {
                TextRange abs = element.getTextRange();
                TextRange rel = new TextRange(
                        abs.getStartOffset() - blockStartOffset,
                        abs.getEndOffset() - blockStartOffset
                );
                result.put(rel, element);
            }
            return true;
        });
        return result;
    }

    public static boolean hasOverlappingRanges(List<TextRange> ranges) {
        for (int i = 0; i < ranges.size() - 1; i++) {
            TextRange current = ranges.get(i);
            TextRange next = ranges.get(i + 1);
            if (current.intersects(next)) {
                return true;
            }
        }
        return false;
    }

    @NotNull
    public static String sqlCoteJsElement(@NotNull PsiElement element) {
        String text = element.getText();
        if (text == null || text.isBlank()) return "NULL";
        if (text.contains("\n") || text.contains("\r")) {
            return ("\"\"\"" + text.substring(3, text.length() - 3) + "\"\"\"").replace("$", "a");
        } else {
            return ("'" + text.substring(1, text.length() - 1) + "'").replace("$", "a");
        }
    }
}
