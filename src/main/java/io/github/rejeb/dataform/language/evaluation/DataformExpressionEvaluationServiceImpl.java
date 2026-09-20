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
import com.intellij.openapi.application.ApplicationManager;
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
import io.github.rejeb.dataform.language.SqlxFileType;
import io.github.rejeb.dataform.language.folding.DataformFoldingRefresher;
import io.github.rejeb.dataform.language.folding.DataformInjectedExpressions;
import io.github.rejeb.dataform.language.index.DataformJsFileIndex;
import io.github.rejeb.dataform.language.schema.sql.DataformSchemaEvent;
import io.github.rejeb.dataform.language.setup.NodeScriptRunner;
import io.github.rejeb.dataform.language.util.DataformProjectLayout;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
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
    private static final long FAILURE_COOLDOWN_MS = 30_000;

    private enum PassOutcome { UP_TO_DATE, STORED, UNCHANGED, UPDATED, HARNESS_FAILURE }

    private final Project project;
    private final Map<String, Map<String, DataformEvaluationResult>> cache = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> generations = new ConcurrentHashMap<>();
    private final Map<String, AtomicBoolean> running = new ConcurrentHashMap<>();
    private final Map<String, Boolean> pending = new ConcurrentHashMap<>();
    private final AtomicLong modificationCount = new AtomicLong();
    private volatile long failedUntil;
    private volatile Evaluator evaluator = defaultEvaluator();

    /** Computes the values of the given sources of a file, as Node does. */
    @FunctionalInterface
    public interface Evaluator {
        @NotNull List<DataformEvaluationResult> evaluate(@NotNull PsiFile file,
                                                         @NotNull List<String> sources);
    }

    public DataformExpressionEvaluationServiceImpl(@NotNull Project project) {
        this.project = project;
        PsiManager.getInstance(project).addPsiTreeChangeListener(new PsiTreeChangeAdapter() {
            @Override
            public void childrenChanged(@NotNull PsiTreeChangeEvent event) {
                onPsiChanged(event.getFile());
            }
        }, this);
        project.getMessageBus().connect(this)
                .subscribe(DataformSchemaEvent.TOPIC, (DataformSchemaEvent) this::markEnvironmentChanged);
    }

    /**
     * An edit never drops a value: the next pass, asked for when the file comes back into focus,
     * computes every value anew. A change to what every file depends on only lifts the cooldown a
     * failed run put in place, so that the fix is tried at once.
     */
    private void onPsiChanged(@Nullable PsiFile file) {
        VirtualFile virtualFile = file == null ? null : file.getVirtualFile();
        if (virtualFile == null) {
            return;
        }
        if (DataformJsFileIndex.isDataformJsFile(virtualFile)
                || DataformWorkflowSettingsValueResolver.WORKFLOW_SETTINGS_FILE_NAME.equals(virtualFile.getName())) {
            markEnvironmentChanged();
        }
    }

    @Override
    public @NotNull Set<String> includeNames(@Nullable VirtualFile context) {
        return DataformProjectLayout.includeNames(context);
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
    public void invalidate(@NotNull VirtualFile file) {
        String url = file.getUrl();
        pending.remove(url);
        if (cache.remove(url) != null) {
            modificationCount.incrementAndGet();
        }
    }

    @Override
    public void invalidateAll() {
        failedUntil = 0;
        if (!cache.isEmpty()) {
            cache.clear();
            modificationCount.incrementAndGet();
        }
    }

    @Override
    public void markEnvironmentChanged() {
        failedUntil = 0;
    }

    @Override
    public void dispose() {
        generations.clear();
        cache.clear();
        pending.clear();
        running.clear();
    }

    private void runPass(@NotNull VirtualFile file) {
        runPass(file, false);
    }

    /**
     * @param ignoreCooldown whether to run even while the harness is on cooldown after a failure,
     *                       which an explicit request for a pass wants
     */
    private void runPass(@NotNull VirtualFile file, boolean ignoreCooldown) {
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
            if (!ignoreCooldown && System.currentTimeMillis() < failedUntil) {
                return;
            }
            List<DataformExpression> expressions = ReadAction.nonBlocking(
                    () -> collect(psiFile)).executeSynchronously();
            PassOutcome outcome = evaluateAll(file, psiFile, expressions);
            if (outcome == PassOutcome.HARNESS_FAILURE) {
                failedUntil = System.currentTimeMillis() + FAILURE_COOLDOWN_MS;
                return;
            }
            if (outcome == PassOutcome.UPDATED) {
                project.getMessageBus().syncPublisher(DataformEvaluationEvent.TOPIC).onValuesUpdated(file);
                DataformFoldingRefresher.refresh(project, file);
            } else if (outcome == PassOutcome.UNCHANGED) {
                DataformFoldingRefresher.collapse(project, file);
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
        Set<String> names = new HashSet<>(DataformProjectLayout.includeNames(virtualFile));
        names.addAll(indexedIncludeNames());

        List<DataformExpression> expressions = new ArrayList<>();
        if (SqlxFileType.INSTANCE.equals(virtualFile.getFileType())) {
            expressions.addAll(DataformExpressionCollector.collectSqlxTemplates(psiFile));
            expressions.addAll(DataformInjectedExpressions.includesReferences(psiFile, names));
        } else {
            expressions.addAll(DataformExpressionCollector.collectJsTemplateSubstitutions(psiFile));
            expressions.addAll(
                    DataformExpressionCollector.collectIncludesReferenceElements(psiFile, names).stream()
                            .map(DataformExpressionCollector.FoldablePart::expression).toList());
        }
        return expressions;
    }

    @NotNull
    private Set<String> indexedIncludeNames() {
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
     * Evaluates every deterministic expression of the file and replaces its values with the
     * results. A hole whose text stood still may still depend on something that moved, so nothing
     * is kept from the previous pass. Values identical to the previous ones are reported as
     * {@link PassOutcome#UNCHANGED}: the file coming back into focus only needs its folds collapsed
     * again, not its injections and folding rebuilt, which is what dropping the caches costs every
     * open editor of the project.
     */
    private PassOutcome evaluateAll(@NotNull VirtualFile file,
                                    @NotNull PsiFile psiFile,
                                    @NotNull List<DataformExpression> expressions) {
        Set<String> sources = new LinkedHashSet<>();
        for (DataformExpression expression : expressions) {
            if (!DataformTemplateSyntax.isDeterministic(expression.source())) {
                continue;
            }
            sources.add(expression.source());
            if (sources.size() >= MAX_EXPRESSIONS_PER_FILE) {
                break;
            }
        }
        Map<String, DataformEvaluationResult> previous = cache.get(file.getUrl());
        if (sources.isEmpty()) {
            cache.put(file.getUrl(), new ConcurrentHashMap<>());
            return previous == null || previous.isEmpty() ? PassOutcome.UP_TO_DATE : PassOutcome.STORED;
        }
        List<DataformEvaluationResult> results = evaluator.evaluate(psiFile, List.copyOf(sources));
        if (results.isEmpty()) {
            return PassOutcome.HARNESS_FAILURE;
        }
        Map<String, DataformEvaluationResult> values = new ConcurrentHashMap<>();
        boolean resolvedAny = false;
        for (DataformEvaluationResult result : results) {
            values.put(result.source(), result);
            resolvedAny |= result.isResolved();
            if (!result.isResolved()) {
                LOG.info("Dataform expression not resolved in " + file.getName() + ": ["
                        + result.source().replace('\n', ' ') + "] -> " + result.error());
            }
        }
        boolean unchanged = previous != null && previous.equals(values);
        cache.put(file.getUrl(), values);
        if (unchanged) {
            return resolvedAny ? PassOutcome.UNCHANGED : PassOutcome.STORED;
        }
        modificationCount.incrementAndGet();
        return resolvedAny ? PassOutcome.UPDATED : PassOutcome.STORED;
    }

    /**
     * Node, except under the test framework, where an external process would answer at a moment
     * no test controls and overwrite the values a test seeded. A test that wants a pass installs
     * an evaluator of its own.
     */
    private @NotNull Evaluator defaultEvaluator() {
        return ApplicationManager.getApplication().isUnitTestMode()
                ? (file, sources) -> List.of()
                : this::evaluateWithNode;
    }

    @NotNull
    private List<DataformEvaluationResult> evaluateWithNode(@NotNull PsiFile psiFile,
                                                            @NotNull List<String> sources) {
        List<String> nodePaths = DataformEvaluationContextBuilder.nodePaths(project);
        DataformEvaluationContext context = ReadAction.nonBlocking(
                () -> DataformEvaluationContextBuilder.build(project, psiFile, nodePaths)).executeSynchronously();
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
    public void setEvaluator(@NotNull Evaluator evaluator) {
        this.evaluator = evaluator;
        failedUntil = 0;
    }

    /**
     * Puts the default evaluator back and clears the failure cooldown, for the tear-down of a test
     * that installed an evaluator: the light project outlives the test class, and so does this
     * service.
     */
    @TestOnly
    public void resetEvaluator() {
        this.evaluator = defaultEvaluator();
        failedUntil = 0;
    }

    /** Runs an evaluation pass for the file where it is called, without the debounce. */
    @TestOnly
    public void runPassNow(@NotNull VirtualFile file) {
        runPass(file, true);
    }

    @TestOnly
    public void putCachedValue(@NotNull VirtualFile file, @NotNull String source, @NotNull String value) {
        cache.computeIfAbsent(file.getUrl(), key -> new ConcurrentHashMap<>())
                .put(source, DataformEvaluationResult.resolved(source, value));
        modificationCount.incrementAndGet();
    }

}
