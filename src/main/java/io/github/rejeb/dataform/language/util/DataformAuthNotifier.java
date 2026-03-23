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

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class DataformAuthNotifier {

    private static final Set<String> NOTIFIED_PROJECTS =
            ConcurrentHashMap.newKeySet();

    private DataformAuthNotifier() {}

    public static void notifyAuthRequired(@NotNull Project project) {
        if (!NOTIFIED_PROJECTS.add(project.getLocationHash())) return;

        NotificationGroupManager.getInstance()
                .getNotificationGroup("Dataform.Notifications")
                .createNotification(
                        "Dataform: BigQuery authentication required",
                        """
                        Schema extraction requires valid Google credentials.<br><br>
                        Configure one of the following:
                        <ul>
                          <li>Run <b>gcloud auth application-default login</b></li>
                          <li>Set env var <b>GOOGLE_APPLICATION_CREDENTIALS</b> to a service account key file</li>
                          <li>Run on GCE/GKE with the appropriate IAM scopes</li>
                        </ul>
                        """,
                        NotificationType.WARNING
                )
                .notify(project);
    }
}
