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
import com.intellij.ui.scale.JBUIScale;

import javax.swing.JToggleButton;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;

public class SegmentedSwitchTest extends BasePlatformTestCase {

    private final List<String> calls = new ArrayList<>();

    private SegmentedSwitch<String> newSwitch() {
        return new SegmentedSwitch<>(List.of("a", "b", "c"), String::toUpperCase, "a", calls::add);
    }

    private static List<JToggleButton> buttons(SegmentedSwitch<String> segmented) {
        List<JToggleButton> buttons = new ArrayList<>();
        for (Component child : segmented.getComponents()) {
            if (child instanceof JToggleButton button) buttons.add(button);
        }
        return buttons;
    }

    public void testEveryValueHasAButtonWithItsLabel() {
        assertEquals(List.of("A", "B", "C"), buttons(newSwitch()).stream().map(JToggleButton::getText).toList());
    }

    public void testTheInitialValueIsPressed() {
        SegmentedSwitch<String> segmented = newSwitch();
        assertEquals("a", segmented.selected());
        assertEquals(List.of(true, false, false), buttons(segmented).stream().map(JToggleButton::isSelected).toList());
        assertTrue(calls.isEmpty());
    }

    public void testSelectingAValueCallsBackOnceAndPressesOnlyThatButton() {
        SegmentedSwitch<String> segmented = newSwitch();

        segmented.select("b");

        assertEquals(List.of("b"), calls);
        assertEquals("b", segmented.selected());
        assertEquals(List.of(false, true, false), buttons(segmented).stream().map(JToggleButton::isSelected).toList());
    }

    public void testSelectingTheCurrentValueDoesNotCallBack() {
        SegmentedSwitch<String> segmented = newSwitch();

        segmented.select("a");

        assertTrue(calls.isEmpty());
    }

    public void testClickingAButtonSelectsItsValue() {
        SegmentedSwitch<String> segmented = newSwitch();

        buttons(segmented).get(2).doClick();

        assertEquals("c", segmented.selected());
        assertEquals(List.of("c"), calls);
    }

    public void testClickingThePressedButtonKeepsItPressedAndDoesNotCallBack() {
        SegmentedSwitch<String> segmented = newSwitch();

        buttons(segmented).get(0).doClick();

        assertTrue(buttons(segmented).get(0).isSelected());
        assertTrue(calls.isEmpty());
    }

    public void testTheSwitchIs26PixelsHighAnd20WhenCompact() {
        SegmentedSwitch<String> segmented = newSwitch();
        assertEquals(JBUIScale.scale(26), segmented.getPreferredSize().height);

        segmented.setCompact(true);

        assertEquals(JBUIScale.scale(20), segmented.getPreferredSize().height);
    }

    public void testAnUnknownValueIsRefused() {
        SegmentedSwitch<String> segmented = newSwitch();
        try {
            segmented.select("z");
            fail("an unknown value was accepted");
        } catch (IllegalArgumentException expected) {
            assertEquals("a", segmented.selected());
        }
    }
}
