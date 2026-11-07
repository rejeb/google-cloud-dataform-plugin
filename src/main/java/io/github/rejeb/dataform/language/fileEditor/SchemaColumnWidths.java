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
package io.github.rejeb.dataform.language.fileEditor;

import com.intellij.util.ui.JBUI;

/**
 * The widths of the Name, Type, Mode and Description columns of a schema table: the width is
 * shared two, one, one and three parts, and a column whose part falls below its minimum keeps the
 * minimum while the others share the rest.
 */
final class SchemaColumnWidths {

    private static final int[] PARTS = {2, 1, 1, 3};
    private static final int[] MINIMUMS = {170, 90, 90, 150};

    private SchemaColumnWidths() {
    }

    /**
     * The width of each column of a table of the given width, adding up to it unless the minimums
     * do not fit.
     */
    static int[] of(int width) {
        int count = PARTS.length;
        int[] widths = new int[count];
        boolean[] fixed = new boolean[count];
        boolean changed = true;
        while (changed) {
            changed = false;
            int free = width;
            int parts = 0;
            for (int i = 0; i < count; i++) {
                if (fixed[i]) free -= widths[i];
                else parts += PARTS[i];
            }
            for (int i = 0; i < count && parts > 0; i++) {
                if (!fixed[i] && (double) free * PARTS[i] / parts < JBUI.scale(MINIMUMS[i])) {
                    widths[i] = JBUI.scale(MINIMUMS[i]);
                    fixed[i] = true;
                    changed = true;
                }
            }
            if (!changed) share(widths, fixed, free, parts);
        }
        return widths;
    }

    private static void share(int[] widths, boolean[] fixed, int free, int parts) {
        int left = free;
        int partsLeft = parts;
        for (int i = 0; i < widths.length; i++) {
            if (fixed[i]) continue;
            widths[i] = partsLeft == PARTS[i] ? left : Math.round((float) left * PARTS[i] / partsLeft);
            left -= widths[i];
            partsLeft -= PARTS[i];
        }
    }
}
