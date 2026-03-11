package io.github.rejeb.dataform.language.util;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shows a one-time balloon notification when BigQuery credentials are missing.
 */
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
