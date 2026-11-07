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
package io.github.rejeb.dataform.language.schema.sql;

import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import javax.swing.KeyStroke;
import java.util.List;

public class ShowColumnUsagesShortcutTest extends BasePlatformTestCase {

    private static final String ACTION = "Dataform.ShowColumnUsages";

    public void testTheShortcutIsAltShiftU() {
        Keymap keymap = KeymapManager.getInstance().getActiveKeymap();
        Shortcut[] shortcuts = keymap.getShortcuts(ACTION);
        assertEquals(1, shortcuts.length);
        assertEquals(new KeyboardShortcut(KeyStroke.getKeyStroke("alt shift U"), null), shortcuts[0]);
    }

    public void testNoOtherActionUsesTheShortcut() {
        Keymap keymap = KeymapManager.getInstance().getActiveKeymap();
        assertEquals(List.of(ACTION), keymap.getActionIdList(keymap.getShortcuts(ACTION)[0]));
    }
}
