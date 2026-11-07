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
package io.github.rejeb.dataform.language.gcp.execution.workflow.runconfig.ui;

import com.intellij.util.ui.JBUI;
import io.github.rejeb.dataform.language.gcp.execution.workflow.runconfig.ui.StatementTimeline.Bar;
import org.junit.jupiter.api.Test;

import java.awt.Rectangle;

import static io.github.rejeb.dataform.language.gcp.execution.workflow.runconfig.ui.TimelineCellRenderer.LabelPlacement.AFTER;
import static io.github.rejeb.dataform.language.gcp.execution.workflow.runconfig.ui.TimelineCellRenderer.LabelPlacement.INSIDE;
import static io.github.rejeb.dataform.language.gcp.execution.workflow.runconfig.ui.TimelineCellRenderer.LabelPlacement.NONE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimelineCellRendererTest {

    @Test
    void aTinyBarKeepsAMinimumWidth() {
        assertEquals(JBUI.scale(4), TimelineCellRenderer.barBounds(new Bar(0.5, 0.5001), 500, 28).width);
    }

    @Test
    void aBarNeverLeavesTheTrack() {
        Rectangle bar = TimelineCellRenderer.barBounds(new Bar(0.999, 1.0), 500, 28);

        assertTrue(bar.x + bar.width <= 500);
    }

    @Test
    void aBarIsCentredInTheRow() {
        assertEquals((28 - JBUI.scale(16)) / 2, TimelineCellRenderer.barBounds(new Bar(0.1, 0.2), 500, 28).y);
    }

    @Test
    void theLabelGoesAfterTheBarWhenItFits() {
        assertEquals(AFTER, TimelineCellRenderer.placeLabel(new Rectangle(10, 0, 20, 16), 40, 500));
    }

    @Test
    void theLabelGoesInsideAWideBarAtTheTrackEnd() {
        assertEquals(INSIDE, TimelineCellRenderer.placeLabel(new Rectangle(60, 0, 430, 16), 60, 500));
    }

    @Test
    void aLabelThatFitsNowhereIsDropped() {
        assertEquals(NONE, TimelineCellRenderer.placeLabel(new Rectangle(480, 0, 10, 16), 60, 500));
    }
}
