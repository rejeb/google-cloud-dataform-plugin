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
package io.github.rejeb.dataform.language.formatting;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import io.github.rejeb.dataform.language.psi.SharedTokenTypes;
import io.github.rejeb.dataform.language.psi.SqlxFile;
import io.github.rejeb.dataform.language.psi.SqlxSqlBlock;
import io.github.rejeb.dataform.language.settings.DataformToolsSettings;
import io.github.rejeb.dataform.language.util.Utils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class SqlxSqlfluffFormatter {

    private static final Logger LOG = Logger.getInstance(SqlxSqlfluffFormatter.class);
    private static final String PLACEHOLDER_PREFIX = "__SQLX_TPL_";
    private static final String PLACEHOLDER_SUFFIX = "__";
    private static final int SQLFLUFF_TIMEOUT_MS = 10_000;

    public static List<CompletableFuture<BlockChange>> processText(@NotNull PsiFile file, @NotNull TextRange rangeToReformat) {
        PsiFile hostFile = resolveHostFile(file);
        if (!(hostFile instanceof SqlxFile)) {
            return List.of();
        }

        Project project = hostFile.getProject();
        DataformToolsSettings toolsSettings = DataformToolsSettings.getInstance();
        String sqlfluffPath = toolsSettings.getSqlfluffExecutablePath();

        if (sqlfluffPath.isEmpty() || !new File(sqlfluffPath).isFile()) {
            NotificationGroupManager.getInstance()
                    .getNotificationGroup("Dataform.Notifications")
                    .createNotification(
                            "SQLFluff is not configured",
                            "Configure it in Settings > Tools > Dataform to enable SQL formatting.",
                            NotificationType.WARNING
                    )
                    .notify(project);
            return List.of();
        }

        TextRange effectiveRange = file.getContext() != null
                ? hostFile.getTextRange()
                : rangeToReformat;

        List<CompletableFuture<BlockChange>> futures = new ArrayList<>();
        PsiTreeUtil.processElements(hostFile, element -> {
            if (element instanceof SqlxSqlBlock sqlBlock) {
                if (sqlBlock.getTextRange().intersects(effectiveRange)) {
                    CompletableFuture<BlockChange> future = scheduleBlockFormat(sqlBlock, toolsSettings);
                    if (future != null) futures.add(future);
                }
            }
            return true;
        });

        return futures;
    }

    /**
     * Resolves the effective host SqlxFile from either the file itself or an injected fragment.
     */
    private static PsiFile resolveHostFile(@NotNull PsiFile file) {
        if (file.getContext() != null) {
            return file.getContext().getContainingFile();
        }
        return file;
    }

    private static CompletableFuture<BlockChange> scheduleBlockFormat(SqlxSqlBlock sqlBlock,
                                                                      DataformToolsSettings settings) {
        String blockText = sqlBlock.getText();
        if (blockText == null || blockText.isBlank()) return null;

        TextRange blockRange = sqlBlock.getTextRange();
        Map<Integer, String> templateExpressions = collectTemplateExpressions(sqlBlock);
        Map<String, String> placeholderMapping = new LinkedHashMap<>();
        String sqlWithPlaceholders = replaceTemplateExpressions(blockText, templateExpressions, placeholderMapping);

        return CompletableFuture
                .supplyAsync(() -> runSqlfluff(sqlWithPlaceholders, settings),
                        runnable -> ApplicationManager.getApplication().executeOnPooledThread(runnable))
                .thenApply(formatted -> {
                    if (formatted == null) return null;
                    String restored = restorePlaceholders(reapplyBoundaryWhitespace(formatted, blockText), placeholderMapping);
                    return restored.equals(blockText) ? null : new BlockChange(blockRange, restored);
                });
    }

    public static void applyChanges(Project project, Document document,
                                    List<CompletableFuture<BlockChange>> futures) {
        List<BlockChange> changes = futures.stream()
                .map(f -> f.getNow(null))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(c -> -c.range().getStartOffset()))
                .toList();

        if (changes.isEmpty()) return;

        WriteCommandAction.runWriteCommandAction(project, () -> {
            for (BlockChange change : changes) {
                document.replaceString(change.range().getStartOffset(), change.range().getEndOffset(), change.text());
            }
            PsiDocumentManager.getInstance(project).commitDocument(document);
        });
    }

    private static Map<Integer, String> collectTemplateExpressions(SqlxSqlBlock sqlBlock) {
        Map<Integer, String> result = new LinkedHashMap<>();
        int blockStart = sqlBlock.getTextRange().getStartOffset();
        PsiTreeUtil.processElements(sqlBlock, element -> {
            IElementType type = element.getNode().getElementType();
            if (type == SharedTokenTypes.TEMPLATE_EXPRESSION) {
                int relativeStart = element.getTextRange().getStartOffset() - blockStart;
                result.put(relativeStart, element.getText());
            }
            return true;
        });
        return result;
    }

    private static String replaceTemplateExpressions(String sql, Map<Integer, String> templateExpressions,
                                                     Map<String, String> placeholderMapping) {
        if (templateExpressions.isEmpty()) return sql;

        List<Map.Entry<Integer, String>> entries = new ArrayList<>(templateExpressions.entrySet());
        entries.sort(Comparator.comparingInt(e -> -e.getKey()));

        StringBuilder result = new StringBuilder(sql);
        int counter = entries.size() - 1;

        for (Map.Entry<Integer, String> entry : entries) {
            int start = entry.getKey();
            String original = entry.getValue();
            String placeholder = PLACEHOLDER_PREFIX + counter + PLACEHOLDER_SUFFIX;
            placeholderMapping.put(placeholder, original);
            result.replace(start, start + original.length(), placeholder);
            counter--;
        }

        return result.toString();
    }

    private static String reapplyBoundaryWhitespace(String formatted, String original) {
        if (formatted == null) return null;
        String leading = leadingWhitespace(original);
        String trailing = trailingWhitespace(original);
        String result = formatted;
        if (!leading.isEmpty() && !result.startsWith(leading)) {
            result = leading + result.stripLeading();
        }
        if (!trailing.isEmpty() && !result.endsWith(trailing)) {
            result = result.stripTrailing() + trailing;
        }
        return result;
    }

    private static String leadingWhitespace(String text) {
        int i = 0;
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) i++;
        return text.substring(0, i);
    }

    private static String trailingWhitespace(String text) {
        int i = text.length();
        while (i > 0 && Character.isWhitespace(text.charAt(i - 1))) i--;
        return text.substring(i);
    }

    private static String restorePlaceholders(String formatted, Map<String, String> placeholderMapping) {
        String result = formatted;
        for (Map.Entry<String, String> entry : placeholderMapping.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
            result = result.replace(entry.getKey().toLowerCase(), entry.getValue());
        }
        return result;
    }

    private static String runSqlfluff(String sql, DataformToolsSettings settings) {
        try {
            CapturingProcessHandler handler = getCapturingProcessHandler(settings);
            handler.getProcessInput().write(sql.getBytes(StandardCharsets.UTF_8));
            handler.getProcessInput().close();

            ProcessOutput output = handler.runProcess(SQLFLUFF_TIMEOUT_MS);

            if (output.getExitCode() > 1) {
                LOG.warn("sqlfluff exited with code " + output.getExitCode() + ": " + output.getStderr());
                return null;
            }

            String stdout = output.getStdout();
            return stdout.isEmpty() ? null : stdout;

        } catch (Exception e) {
            LOG.warn("Failed to run sqlfluff", e);
            return null;
        }
    }

    private static @NotNull CapturingProcessHandler getCapturingProcessHandler(DataformToolsSettings settings) throws ExecutionException {
        GeneralCommandLine cmd = new GeneralCommandLine();
        cmd.setExePath(settings.getSqlfluffExecutablePath());
        cmd.addParameter("fix");
        cmd.addParameter("--dialect");
        cmd.addParameter("bigquery");
        cmd.addParameter("-f");
        cmd.addParameter("-");

        String configPath = settings.getSqlfluffConfigPath();
        if (!configPath.isEmpty()) {
            cmd.addParameter("--config");
            cmd.addParameter(configPath);
        }

        String extraArgs = settings.getSqlfluffExtraArgs();
        if (!extraArgs.isEmpty()) {
            for (String arg : extraArgs.split("\\s+")) {
                if (!arg.isEmpty()) {
                    cmd.addParameter(arg);
                }
            }
        }

        cmd.setCharset(StandardCharsets.UTF_8);

        return new CapturingProcessHandler(cmd);
    }
}
