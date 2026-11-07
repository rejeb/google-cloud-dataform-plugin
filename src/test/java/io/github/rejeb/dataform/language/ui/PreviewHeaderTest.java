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
package io.github.rejeb.dataform.language.ui;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.UIUtil;

import javax.swing.JLabel;
import java.awt.BorderLayout;

public class PreviewHeaderTest extends BasePlatformTestCase {

    public void testSummaryIsSmallAndGrayed() {
        PreviewHeader header = new PreviewHeader();
        header.setSummary("Direct dependencies of a.sqlx");
        assertEquals("Direct dependencies of a.sqlx", header.summaryText());
        assertEquals(JBFont.medium().getSize(), header.summaryLabel().getFont().getSize());
        assertEquals(UIUtil.getContextHelpForeground(), header.summaryLabel().getForeground());
    }

    public void testTrailingComponentSitsAtTheRight() {
        PreviewHeader header = new PreviewHeader();
        JLabel trailing = new JLabel("x");
        header.setTrailing(trailing);
        assertSame(trailing, ((BorderLayout) header.getLayout()).getLayoutComponent(BorderLayout.EAST));
    }
}
