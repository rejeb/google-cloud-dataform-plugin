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
package io.github.rejeb.dataform.language.lineage.view;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.InlineBanner;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.gcp.auth.DataformCredentialsService;
import io.github.rejeb.dataform.language.lineage.dataplex.DataplexLineageException;
import io.github.rejeb.dataform.language.lineage.dataplex.DataplexLineageService;
import io.github.rejeb.dataform.language.lineage.dataplex.TimeRange;
import io.github.rejeb.dataform.language.ui.Banners;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The banner shown over the graph when Knowledge Catalog has no lineage to give: why, and what to
 * do about it. Every banner can retry; those over an empty graph can also go back to the Dataform lineage.
 */
final class DataplexStateBanner {

    static final String ROLE = "roles/datalineage.viewer";
    private static final String RETRY = "Retry";
    private static final String SHOW_DATAFORM = "Show Dataform lineage";
    private static final String API_PAGE = "https://console.cloud.google.com/apis/library/datalineage.googleapis.com?project=";

    /**
     * Why there is no recorded lineage to show.
     */
    enum State {
        API_DISABLED, PERMISSION_DENIED, NOT_SIGNED_IN, NOTHING_RECORDED, FAILED
    }

    private final String title;
    private final String message;
    private final EditorNotificationPanel.Status status;
    private final Map<String, Runnable> actions = new LinkedHashMap<>();
    private final InlineBanner banner;

    private DataplexStateBanner(@NotNull String title, @NotNull String message,
                                @NotNull EditorNotificationPanel.Status status) {
        this.title = title;
        this.message = message;
        this.status = status;
        this.banner = switch (status) {
            case Error -> Banners.error(message);
            case Warning -> Banners.warning(message);
            default -> Banners.info(message);
        };
        Banners.setMessage(banner, title, message);
    }

    /**
     * @return the state a failed read of the recorded lineage is reported as
     */
    static @NotNull State stateOf(@NotNull DataplexLineageException.Kind kind) {
        return switch (kind) {
            case API_DISABLED -> State.API_DISABLED;
            case PERMISSION_DENIED -> State.PERMISSION_DENIED;
            case NOT_SIGNED_IN -> State.NOT_SIGNED_IN;
            case OTHER -> State.FAILED;
        };
    }

    /**
     * @param project      the project signing in from the banner
     * @param state        why there is nothing to show
     * @param projectId    the Google Cloud project the lineage was asked in
     * @param range        how far back the lineage was asked for
     * @param detail       the message of the failure, shown for {@link State#FAILED}
     * @param retry        reads the lineage again
     * @param showDataform goes back to the Dataform lineage
     */
    static @NotNull DataplexStateBanner of(@NotNull Project project, @NotNull State state, @NotNull String projectId,
                                           @NotNull TimeRange range, @Nullable String detail,
                                           @NotNull Runnable retry, @NotNull Runnable showDataform) {
        DataplexStateBanner banner = switch (state) {
            case API_DISABLED -> new DataplexStateBanner("The Data Lineage API is not enabled in " + projectId,
                    "Knowledge Catalog records lineage only after the API is enabled, and only for jobs that run afterwards.",
                    EditorNotificationPanel.Status.Warning)
                    .action("Enable the API", () -> BrowserUtil.browse(API_PAGE + projectId));
            case PERMISSION_DENIED -> new DataplexStateBanner("Your account cannot read lineage in " + projectId,
                    "Ask for the role " + ROLE + " on this project, and on each project whose tables you want to see.",
                    EditorNotificationPanel.Status.Error)
                    .action("Copy the role name", () -> CopyPasteManager.getInstance().setContents(new StringSelection(ROLE)));
            case NOT_SIGNED_IN -> new DataplexStateBanner("Sign in to Google Cloud to read the Knowledge Catalog lineage",
                    "The recorded lineage is read with your Google account.",
                    EditorNotificationPanel.Status.Warning)
                    .action("Sign in", () -> DataformCredentialsService.getInstance().signIn(project));
            case NOTHING_RECORDED -> new DataplexStateBanner(
                    "No lineage recorded in the " + range.label().toLowerCase(Locale.ROOT),
                    "Lineage appears after a job runs, within 24 hours. Knowledge Catalog keeps it for 30 days.",
                    EditorNotificationPanel.Status.Info);
            case FAILED -> new DataplexStateBanner("Knowledge Catalog cannot answer",
                    detail == null || detail.isBlank() ? "The request failed." : detail,
                    EditorNotificationPanel.Status.Error);
        };
        return banner.action(RETRY, retry).action(SHOW_DATAFORM, showDataform);
    }

    /**
     * The warning laid over a graph that is still shown when reading a newer one failed.
     *
     * @param detail the message of the failure
     * @param retry  reads the lineage again
     */
    static @NotNull DataplexStateBanner updateFailed(@Nullable String detail, @NotNull Runnable retry) {
        String reason = detail == null || detail.isBlank() ? "the request failed" : detail;
        return new DataplexStateBanner("Could not update the Knowledge Catalog lineage: " + reason,
                "The lineage loaded before stays shown.", EditorNotificationPanel.Status.Warning)
                .action(RETRY, retry);
    }

    private @NotNull DataplexStateBanner action(@NotNull String label, @NotNull Runnable run) {
        actions.put(label, run);
        banner.addAction(label, run);
        return this;
    }

    /**
     * @return the bold first line of the banner
     */
    @NotNull String title() {
        return title;
    }

    /**
     * @return the explanation under the title
     */
    @NotNull String message() {
        return message;
    }

    /**
     * @return whether the banner informs, warns or reports an error
     */
    @NotNull EditorNotificationPanel.Status status() {
        return status;
    }

    /**
     * @return the labels of the links of the banner, in order
     */
    @NotNull List<String> actions() {
        return new ArrayList<>(actions.keySet());
    }

    /**
     * Runs the link with the given label.
     *
     * @throws IllegalArgumentException when the banner has no such link
     */
    void run(@NotNull String label) {
        Runnable action = actions.get(label);
        if (action == null) throw new IllegalArgumentException("No action: " + label);
        action.run();
    }

    static @NotNull String projectIdOf(@NotNull DataplexLineageService service, @Nullable CompiledGraph compiled) {
        if (compiled == null) return "this project";
        try {
            return service.scopeOf(compiled).projectId();
        } catch (RuntimeException e) {
            return "this project";
        }
    }

    /**
     * @return the banner to lay over the graph
     */
    @NotNull InlineBanner component() {
        return banner;
    }
}
