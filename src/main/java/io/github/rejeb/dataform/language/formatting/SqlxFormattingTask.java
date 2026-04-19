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

import com.intellij.formatting.*;
import com.intellij.formatting.service.AsyncDocumentFormattingService;
import com.intellij.formatting.service.AsyncFormattingRequest;
import com.intellij.lang.LanguageFormatting;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.formatter.DocumentBasedFormattingModel;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.github.rejeb.dataform.language.formatting.SqlxSqlfluffFormatter.applyChanges;
import static io.github.rejeb.dataform.language.formatting.SqlxSqlfluffFormatter.processText;

public class SqlxFormattingTask implements AsyncDocumentFormattingService.FormattingTask {
    private static final @NotNull Logger LOGGER = Logger.getInstance(SqlxFormattingTask.class);
    private final AsyncFormattingRequest formattingRequest;
    private final AtomicBoolean underProgress = new AtomicBoolean(false);

    public SqlxFormattingTask(AsyncFormattingRequest formattingRequest) {
        this.formattingRequest = formattingRequest;
    }

    @Override
    public boolean cancel() {
        return false;
    }

    @Override
    public void run() {
        if (underProgress.compareAndSet(false, true)) {
            String fileName = formattingRequest.getContext().getContainingFile().getName();
            try {
                Pair<FormattingModel, FormattingContext> modelAndFormattingContext = ReadAction.computeBlocking(this::doFormat);
                if (modelAndFormattingContext != null) {
                    FormatterEx formatter = FormatterEx.getInstanceEx();
                    CommonCodeStyleSettings.IndentOptions indentOptions = modelAndFormattingContext.getSecond().getCodeStyleSettings()
                            .getIndentOptions(modelAndFormattingContext.getSecond().getContainingFile().getFileType());
                    FormatTextRanges formatTextRanges = new FormatTextRanges(modelAndFormattingContext.getSecond().getFormattingRange(), true);
                    runFormat(formatter, modelAndFormattingContext.getFirst(), modelAndFormattingContext.getSecond(), indentOptions, formatTextRanges);
                } else {
                    formattingRequest.onError("Error formatting element", "Unable to find a suitable formatting builder for file" + fileName);
                }
            } catch (Throwable e) {
                LOGGER.error("Error formatting element" + fileName, e);
                formattingRequest.onError("Error formatting element", e.getMessage());
            }
            underProgress.set(false);
        }
    }

    private Pair<FormattingModel, FormattingContext> doFormat() {
        FormattingContext fc = formattingRequest.getContext();
        PsiFile file = sqlxFile(fc.getContainingFile());
        FormattingModelBuilder builder = LanguageFormatting.INSTANCE.forContext(file);
        Document document = file.getViewProvider().getDocument();

        if (builder != null) {
            FormattingContext fileFormattingContext = FormattingContext.create(file, file.getTextRange(), fc.getCodeStyleSettings(), fc.getFormattingMode());
            FormattingModel originalModel = builder.createModel(fileFormattingContext);

            FormattingModel model = new DocumentBasedFormattingModel(originalModel,
                    document,
                    fc.getProject(), fc.getCodeStyleSettings(), file.getFileType(), file);

            return Pair.create(model, fileFormattingContext);
        } else {
            return null;
        }
    }

    private void runFormat(FormatterEx formatter, FormattingModel model, FormattingContext fc, CommonCodeStyleSettings.IndentOptions indentOptions, FormatTextRanges formatTextRanges) {
        Runnable r = () -> {
            formatter.format(model, fc.getCodeStyleSettings(), indentOptions, formatTextRanges);
            Document document = model.getDocumentModel().getDocument();
            PsiDocumentManager.getInstance(fc.getProject()).commitDocument(document);
            TextRange rangeToProcess = TextRange.from(0, document.getTextLength());
            List<CompletableFuture<BlockChange>> futures = processText(fc.getContainingFile(),  rangeToProcess);
            if (!futures.isEmpty()) {
                CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).thenAccept(ignored -> {
                    applyChanges(fc.getProject(), document, futures);
                    formattingRequest.onTextReady(document.getText());
                });
            } else {
                formattingRequest.onTextReady(document.getText());
            }
        };

        WriteCommandAction.runWriteCommandAction(this.formattingRequest.getContext().getProject(), r);
    }

    private PsiFile sqlxFile(PsiFile file) {
        return file.getContext() != null && file.getContext().getContainingFile() != null ? file.getContext().getContainingFile() : file;
    }

}
