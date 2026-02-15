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
package io.github.rejeb.dataform.language.injection;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import io.github.rejeb.dataform.language.psi.SqlxElementTypes;

import java.util.ArrayList;
import java.util.List;

public class InjectionHelper {
    private static final List<IElementType> JS_ELEMENT_TYPES = List.of(
            SqlxElementTypes.TEMPLATE_EXPRESSION_ELEMENT,
            SqlxElementTypes.JS_LITTERAL_ELEMENT
    );


    public static List<TextRange> collectJsElementRanges(PsiElement sqlBlock) {
        List<TextRange> ranges = new ArrayList<>();
        int blockStartOffset = sqlBlock.getTextRange().getStartOffset();

        System.out.println("  [InjectionHelper] Collecting JS elements from block starting at: " + blockStartOffset);

        PsiTreeUtil.processElements(sqlBlock, element -> {

            IElementType elementType = element.getNode().getElementType();
            if (JS_ELEMENT_TYPES.contains(elementType)) {

                TextRange absoluteRange = element.getTextRange();
                TextRange relativeRange = new TextRange(
                        absoluteRange.getStartOffset() - blockStartOffset,
                        absoluteRange.getEndOffset() - blockStartOffset
                );
                System.out.println("  [InjectionHelper] Found JS element: " + elementType);
                System.out.println("    Absolute range: " + absoluteRange + " (" + element.getText() + ")");
                System.out.println("    Relative range: " + relativeRange);
                ranges.add(relativeRange);
            }
            return true;
        });

        System.out.println("  [InjectionHelper] Total JS elements found: " + ranges.size());
        return ranges;
    }

    public static boolean hasOverlappingRanges(List<TextRange> ranges) {
        System.out.println("  [InjectionHelper] Checking for overlapping ranges (" + ranges.size() + " ranges)");
        for (int i = 0; i < ranges.size() - 1; i++) {
            TextRange current = ranges.get(i);
            TextRange next = ranges.get(i + 1);
            System.out.println("    Comparing range " + i + ": " + current + " with range " + (i+1) + ": " + next);
            if (current.intersects(next)) {
                System.out.println("    -> OVERLAP DETECTED!");
                return true;
            }
        }
        System.out.println("    -> No overlaps");
        return false;
    }
}
