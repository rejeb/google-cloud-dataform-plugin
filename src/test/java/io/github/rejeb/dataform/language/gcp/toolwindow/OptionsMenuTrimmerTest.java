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
package io.github.rejeb.dataform.language.gcp.toolwindow;

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionPopupMenu;
import com.intellij.openapi.actionSystem.ActionUiKind;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.impl.ActionMenuItem;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import javax.swing.JMenu;
import javax.swing.JPopupMenu;
import java.awt.Component;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;

public class OptionsMenuTrimmerTest extends BasePlatformTestCase {

    private final AnAction manage = action("Manage Repositories…");
    private final AnAction create = action("Create Workspace…");
    private final AnAction signOut = action("Sign Out of Google Cloud");
    private final AnAction hide = action("Hide");
    private final OptionsMenuTrimmer trimmer = new OptionsMenuTrimmer(List.of(manage, create, signOut, hide));

    public void testTheOptionsMenuKeepsOnlyTheDataformActions() {
        JPopupMenu popup = popup(item(manage), item(create), separator(), item(signOut), separator(), item(hide),
                separator(), item(action("Move Tab to Editor")), item(action("Speed Search")), separator(),
                new JMenu("View Mode"), new JMenu("Move To"), new JMenu("Resize"), separator(),
                item(action("Remove from Sidebar")), separator(), item(action("Hide")));

        trimmer.actionPopupMenuCreated(menu(ActionPlaces.TOOLWINDOW_POPUP, popup));

        assertEquals(List.of("Manage Repositories…", "Create Workspace…", "---", "Sign Out of Google Cloud", "---", "Hide"),
                labels(popup));
    }

    public void testTheMenusOfOtherToolWindowsAreLeftAlone() {
        JPopupMenu popup = popup(item(action("Show Toolbar")), separator(), new JMenu("View Mode"));

        trimmer.actionPopupMenuCreated(menu(ActionPlaces.TOOLWINDOW_POPUP, popup));

        assertEquals(List.of("Show Toolbar", "---", "View Mode"), labels(popup));
    }

    public void testOtherPopupMenusAreLeftAlone() {
        JPopupMenu popup = popup(item(manage), separator(), item(action("Copy")));

        trimmer.actionPopupMenuCreated(menu(ActionPlaces.EDITOR_POPUP, popup));

        assertEquals(List.of("Manage Repositories…", "---", "Copy"), labels(popup));
    }

    public void testNoSeparatorIsLeftAtTheEnds() {
        JPopupMenu popup = popup(item(action("Move Tab to Editor")), separator(), item(manage), separator(),
                item(action("Remove from Sidebar")));

        trimmer.actionPopupMenuCreated(menu(ActionPlaces.TOOLWINDOW_POPUP, popup));

        assertEquals(List.of("Manage Repositories…"), labels(popup));
    }

    private static AnAction action(String text) {
        return DumbAwareAction.create(text, e -> { });
    }

    private static ActionMenuItem item(AnAction action) {
        ActionMenuItem item = new ActionMenuItem(action, DataContext.EMPTY_CONTEXT, ActionPlaces.TOOLWINDOW_POPUP,
                ActionUiKind.POPUP, false, false, false);
        item.setText(action.getTemplatePresentation().getText());
        return item;
    }

    private static JPopupMenu.Separator separator() {
        return new JPopupMenu.Separator();
    }

    private static JPopupMenu popup(Component... components) {
        JPopupMenu popup = new JPopupMenu();
        for (Component component : components) {
            popup.add(component);
        }
        return popup;
    }

    private static List<String> labels(JPopupMenu popup) {
        return Arrays.stream(popup.getComponents())
                .map(c -> c instanceof JPopupMenu.Separator ? "---" : ((javax.swing.JMenuItem) c).getText())
                .toList();
    }

    private static ActionPopupMenu menu(String place, JPopupMenu popup) {
        return (ActionPopupMenu) Proxy.newProxyInstance(ActionPopupMenu.class.getClassLoader(),
                new Class<?>[]{ActionPopupMenu.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getPlace" -> place;
                    case "getComponent" -> popup;
                    default -> null;
                });
    }
}
