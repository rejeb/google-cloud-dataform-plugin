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
package io.github.rejeb.dataform.language.evaluation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiTreeChangeAdapter;
import com.intellij.psi.PsiTreeChangeEvent;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.ThreadingAssertions;
import io.github.rejeb.dataform.language.SqlxFileType;
import io.github.rejeb.dataform.language.folding.DataformFoldingRefresher;
import io.github.rejeb.dataform.language.index.DataformJsFileIndex;
import io.github.rejeb.dataform.language.schema.sql.DataformSchemaEvent;
import io.github.rejeb.dataform.language.setup.NodeScriptRunner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class DataformExpressionEvaluationServiceImpl
        implements DataformExpressionEvaluationService, Disposable {

    private static final Logger LOG = Logger.getInstance(DataformExpressionEvaluationServiceImpl.class);
    private static final Gson GSON = new GsonBuilder().create();
    private static final Type RESULTS_TYPE = new TypeToken<List<DataformEvaluationResult>>() {
    }.getType();

    private static final int DEBOUNCE_MS = 400;
    private static final int NODE_TIMEOUT_MS = 5_000;
    private static final int MAX_EXPRESSIONS_PER_FILE = 200;
    private static final String INCLUDES_DIRECTORY = "includes";
    private static final String JS_EXTENSION = "js";

    private final Project project;
    private final Map<String, Map<String, DataformEvaluationResult>> cache = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> generations = new ConcurrentHashMap<>();
    private final Map<String, AtomicBoolean> running = new ConcurrentHashMap<>();
    private final Map<String, Boolean> pending = new ConcurrentHashMap<>();
    private final AtomicLong modificationCount = new AtomicLong();
    private volatile Set<String> includeNames = Set.of();

    public DataformExpressionEvaluationServiceImpl(@NotNull Project project) {
        this.project = project;
        PsiManager.getInstance(project).addPsiTreeChangeListener(new PsiTreeChangeAdapter() {
            @Override
            public void childrenChanged(@NotNull PsiTreeChangeEvent event) {
                onPsiChanged(event.getFile());
            }
        }, this);
        project.getMessageBus().connect(this)
                .subscribe(DataformSchemaEvent.TOPIC, (DataformSchemaEvent) this::invalidateAll);
    }

    private void onPsiChanged(@Nullable PsiFile file) {
        VirtualFile virtualFile = file == null ? null : file.getVirtualFile();
        if (virtualFile == null) {
            return;
        }
        if (DataformJsFileIndex.isDataformJsFile(virtualFile)
                || DataformWorkflowSettingsValueResolver.WORKFLOW_SETTINGS_FILE_NAME.equals(virtualFile.getName())) {
            invalidateAll();
        } else {
            invalidate(virtualFile);
        }
    }

    @Override
    public @NotNull Set<String> includeNames(@Nullable VirtualFile context) {
        Set<String> fromDirectory = scanIncludesDirectory(context);
        if (!fromDirectory.isEmpty()) {
            includeNames = fromDirectory;
            return fromDirectory;
        }
        return includeNames;
    }

    /**
     * Lists {@code includes/*.js} by walking up from the given file, so a Dataform project nested in a
     * larger repository finds its own includes without touching the index.
     */
    @NotNull
    private Set<String> scanIncludesDirectory(@Nullable VirtualFile context) {
        VirtualFile directory = context == null || context.isDirectory() ? context : context.getParent();
        while (directory != null) {
            VirtualFile includes = directory.findChild(INCLUDES_DIRECTORY);
            if (includes != null && includes.isDirectory()) {
                Set<String> names = new HashSet<>();
                for (VirtualFile child : includes.getChildren()) {
                    if (!child.isDirectory() && JS_EXTENSION.equals(child.getExtension())) {
                        names.add(child.getNameWithoutExtension());
                    }
                }
                return names;
            }
            directory = directory.getParent();
        }
        return Set.of();
    }

    @Override
    public long getModificationCount() {
        return modificationCount.get();
    }

    @Override
    public @Nullable String getCachedValue(@NotNull VirtualFile file, @NotNull String expressionSource) {
        Map<String, DataformEvaluationResult> values = cache.get(file.getUrl());
        DataformEvaluationResult result = values == null ? null : values.get(expressionSource);
        return result == null ? null : result.value();
    }

    @Override
    public void requestEvaluation(@NotNull PsiFile file) {
        VirtualFile virtualFile = file.getVirtualFile();
        if (virtualFile == null || !virtualFile.isInLocalFileSystem()) {
            return;
        }
        String url = virtualFile.getUrl();
        AtomicLong generation = generations.computeIfAbsent(url, key -> new AtomicLong());
        long scheduled = generation.incrementAndGet();
        AppExecutorUtil.getAppScheduledExecutorService().schedule(() -> {
            if (generation.get() == scheduled) {
                runPass(virtualFile);
            }
        }, DEBOUNCE_MS, TimeUnit.MILLISECONDS);
    }

    @Override
    public @NotNull Map<String, String> evaluateNow(@NotNull PsiFile file,
                                                    @NotNull List<DataformExpression> expressions) {
        ThreadingAssertions.assertBackgroundThread();
        VirtualFile virtualFile = file.getVirtualFile();
        if (virtualFile == null || expressions.isEmpty()) {
            return Map.of();
        }
        evaluateMissing(virtualFile, file, expressions);

        Map<String, String> values = new LinkedHashMap<>();
        for (DataformExpression expression : expressions) {
            String value = getCachedValue(virtualFile, expression.source());
            if (value != null) {
                values.put(expression.source(), value);
            }
        }
        return values;
    }

    @Override
    public void invalidate(@NotNull VirtualFile file) {
        if (cache.remove(file.getUrl()) != null) {
            modificationCount.incrementAndGet();
        }
    }

    @Override
    public void invalidateAll() {
        if (!cache.isEmpty()) {
            cache.clear();
            modificationCount.incrementAndGet();
        }
    }

    @Override
    public void dispose() {
        generations.clear();
        cache.clear();
    }

    private void runPass(@NotNull VirtualFile file) {
        if (project.isDisposed() || !file.isValid()) {
            return;
        }
        String url = file.getUrl();
        AtomicBoolean lock = running.computeIfAbsent(url, key -> new AtomicBoolean());
        if (!lock.compareAndSet(false, true)) {
            pending.put(url, Boolean.TRUE);
            return;
        }
        try {
            if (DumbService.isDumb(project)) {
                return;
            }
            PsiFile psiFile = ReadAction.nonBlocking(
                    () -> PsiManager.getInstance(project).findFile(file)).executeSynchronously();
            if (psiFile == null || !isFileOpen(file)) {
                return;
            }
            List<DataformExpression> expressions = ReadAction.nonBlocking(
                    () -> collect(psiFile)).executeSynchronously();
            if (evaluateMissing(file, psiFile, expressions)) {
                project.getMessageBus().syncPublisher(DataformEvaluationEvent.TOPIC).onValuesUpdated(file);
                DataformFoldingRefresher.refresh(project, file);
            }
        } catch (ProcessCanceledException e) {
            throw e;
        } catch (Exception e) {
            LOG.warn("Dataform expression evaluation failed for " + url, e);
        } finally {
            lock.set(false);
            if (pending.remove(url) != null) {
                runPass(file);
            }
        }
    }

    private boolean isFileOpen(@NotNull VirtualFile file) {
        return ReadAction.nonBlocking(
                () -> FileEditorManager.getInstance(project).isFileOpen(file)).executeSynchronously();
    }

    @NotNull
    private List<DataformExpression> collect(@NotNull PsiFile psiFile) {
        VirtualFile virtualFile = psiFile.getVirtualFile();
        if (virtualFile == null) {
            return List.of();
        }
        Set<String> names = new HashSet<>(includeNames(virtualFile));
        names.addAll(refreshIncludeNames());
        includeNames = names;

        List<DataformExpression> expressions = new ArrayList<>();
        if (SqlxFileType.INSTANCE.equals(virtualFile.getFileType())) {
            expressions.addAll(DataformExpressionCollector.collectSqlxTemplates(psiFile));
            expressions.addAll(DataformExpressionCollector.collectInjectedIncludesReferences(psiFile, names));
        } else {
            expressions.addAll(DataformExpressionCollector.collectJsTemplateSubstitutions(psiFile));
            expressions.addAll(
                    DataformExpressionCollector.collectIncludesReferenceElements(psiFile, names).stream()
                            .map(DataformExpressionCollector.FoldablePart::expression).toList());
        }
        return expressions;
    }

    @NotNull
    private Set<String> refreshIncludeNames() {
        Set<String> names = new HashSet<>();
        for (PsiFile include : DataformJsFileIndex.findDataformJsFiles(project)) {
            VirtualFile file = include == null ? null : include.getVirtualFile();
            if (file != null) {
                names.add(file.getNameWithoutExtension());
            }
        }
        return names;
    }

    /**
     * Evaluates the expressions of the file that have no cached result yet.
     *
     * @return whether at least one new value was cached
     */
    private boolean evaluateMissing(@NotNull VirtualFile file,
                                    @NotNull PsiFile psiFile,
                                    @NotNull List<DataformExpression> expressions) {
        Map<String, DataformEvaluationResult> values =
                cache.computeIfAbsent(file.getUrl(), key -> new ConcurrentHashMap<>());

        List<String> missing = new ArrayList<>();
        for (DataformExpression expression : expressions) {
            if (!DataformTemplateSyntax.isDeterministic(expression.source())) {
                continue;
            }
            if (!values.containsKey(expression.source()) && !missing.contains(expression.source())) {
                missing.add(expression.source());
            }
            if (missing.size() >= MAX_EXPRESSIONS_PER_FILE) {
                break;
            }
        }
        if (missing.isEmpty()) {
            return false;
        }

        List<DataformEvaluationResult> results = evaluate(psiFile, missing);
        if (results.isEmpty()) {
            return false;
        }

        boolean resolvedAny = false;
        for (DataformEvaluationResult result : results) {
            values.put(result.source(), result);
            resolvedAny |= result.isResolved();
            if (!result.isResolved()) {
                LOG.info("Dataform expression not resolved in " + file.getName() + ": ["
                        + result.source().replace('\n', ' ') + "] -> " + result.error());
            }
        }
        modificationCount.incrementAndGet();
        return resolvedAny;
    }

    @NotNull
    private List<DataformEvaluationResult> evaluate(@NotNull PsiFile psiFile, @NotNull List<String> sources) {
        DataformEvaluationContext context = ReadAction.nonBlocking(
                () -> DataformEvaluationContextBuilder.build(project, psiFile)).executeSynchronously();
        String payload = DataformEvalScriptBuilder.payload(context, sources);

        ProcessOutput output = NodeScriptRunner.run(project,
                        DataformEvalScriptBuilder.SCRIPT_NAME,
                        DataformEvalScriptBuilder.script(),
                        payload,
                        NODE_TIMEOUT_MS)
                .orElse(null);
        if (output == null) {
            return List.of();
        }
        if (output.getExitCode() != 0) {
            LOG.warn("Dataform expression evaluation exited with " + output.getExitCode() + ": " + output.getStderr());
            return List.of();
        }
        try {
            List<DataformEvaluationResult> results = GSON.fromJson(output.getStdout(), RESULTS_TYPE);
            return results == null ? List.of() : results;
        } catch (Exception e) {
            LOG.warn("Unparseable Dataform expression evaluation output", e);
            return List.of();
        }
    }

    /**
     * Seeds the cache with a value, so editor features can be tested without a Node process.
     */
    @TestOnly
    public void putCachedValue(@NotNull VirtualFile file, @NotNull String source, @NotNull String value) {
        cache.computeIfAbsent(file.getUrl(), key -> new ConcurrentHashMap<>())
                .put(source, DataformEvaluationResult.resolved(source, value));
        modificationCount.incrementAndGet();
    }

    /**
     * Exposes the cached results of a file, for tests and diagnostics.
     */
    @NotNull
    public Map<String, DataformEvaluationResult> cachedResults(@NotNull VirtualFile file) {
        Map<String, DataformEvaluationResult> values = cache.get(file.getUrl());
        return values == null ? Map.of() : new HashMap<>(values);
    }
}
