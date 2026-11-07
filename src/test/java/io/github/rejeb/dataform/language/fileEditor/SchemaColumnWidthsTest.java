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
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SchemaColumnWidthsTest {

    @Test
    void aWideTableSharesItsWidthTwoOneOneThree() {
        int unit = JBUI.scale(200);

        assertArrayEquals(new int[]{2 * unit, unit, unit, 3 * unit}, SchemaColumnWidths.of(7 * unit));
    }

    @Test
    void aColumnBelowItsMinimumKeepsItAndTheOthersShareTheRest() {
        int width = JBUI.scale(520);

        int[] widths = SchemaColumnWidths.of(width);

        assertArrayEquals(new int[]{JBUI.scale(170), JBUI.scale(90), JBUI.scale(90),
                width - JBUI.scale(170) - 2 * JBUI.scale(90)}, widths);
    }

    @Test
    void aTableNarrowerThanTheMinimumsKeepsEveryMinimum() {
        assertArrayEquals(new int[]{JBUI.scale(170), JBUI.scale(90), JBUI.scale(90), JBUI.scale(150)},
                SchemaColumnWidths.of(JBUI.scale(300)));
    }

    @Test
    void theWidthsAddUpToTheTableWidth() {
        for (int width = JBUI.scale(500); width < JBUI.scale(1500); width += 7) {
            assertEquals(width, Arrays.stream(SchemaColumnWidths.of(width)).sum(), "width " + width);
        }
    }
}
