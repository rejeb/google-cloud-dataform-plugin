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
package io.github.rejeb.dataform.language.util;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import org.jetbrains.annotations.NotNull;

public final class DataformNotifications {

    public static final String GROUP_ID = "Dataform.Notifications";

    private DataformNotifications() {
    }

    /**
     * Creates a notification of the Dataform notification group.
     *
     * @param title   the title, empty for none
     * @param content the message
     * @param type    the severity
     * @return the notification, not shown yet
     */
    public static @NotNull Notification create(@NotNull String title, @NotNull String content,
                                               @NotNull NotificationType type) {
        return NotificationGroupManager.getInstance().getNotificationGroup(GROUP_ID)
                .createNotification(title, content, type);
    }

    /**
     * Creates a notification of the Dataform notification group with no title.
     *
     * @param content the message
     * @param type    the severity
     * @return the notification, not shown yet
     */
    public static @NotNull Notification create(@NotNull String content, @NotNull NotificationType type) {
        return create("", content, type);
    }
}
