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
package io.github.rejeb.dataform.language.gcp.execution.workflow.runconfig;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;

import java.awt.*;
import java.awt.event.AWTEventListener;
import java.awt.event.MouseEvent;

@Service(Service.Level.APP)
public final class LastMousePositionService implements AWTEventListener {

    private Point lastScreenPosition = new Point(0, 0);

    public LastMousePositionService() {
        Toolkit.getDefaultToolkit().addAWTEventListener(
                this, AWTEvent.MOUSE_EVENT_MASK);
    }

    @Override
    public void eventDispatched(AWTEvent event) {
        if (event instanceof MouseEvent me) {
            if (me.getID() == MouseEvent.MOUSE_PRESSED) {
                lastScreenPosition = me.getLocationOnScreen();
            }
        }
    }

    public Point getLastScreenPosition() {
        return new Point(lastScreenPosition);
    }

    public static LastMousePositionService getInstance() {
        return ApplicationManager.getApplication()
                .getService(LastMousePositionService.class);
    }
}