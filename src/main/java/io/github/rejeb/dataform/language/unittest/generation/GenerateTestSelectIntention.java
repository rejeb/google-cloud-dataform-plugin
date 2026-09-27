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
package io.github.rejeb.dataform.language.unittest.generation;

import com.intellij.lang.ASTNode;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.modcommand.Presentation;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import io.github.rejeb.dataform.language.psi.SharedTokenTypes;
import io.github.rejeb.dataform.language.psi.SqlxInputBlock;
import io.github.rejeb.dataform.language.psi.SqlxSqlBlock;
import io.github.rejeb.dataform.language.unittest.schema.TestBlockSchema;
import io.github.rejeb.dataform.language.unittest.schema.TestSchemaResolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Fills an input block or the expected output of a Dataform unit test with a {@code SELECT} listing
 * every column of the table the block stands for.
 */
public final class GenerateTestSelectIntention implements ModCommandAction {

    @Override
    public @NotNull String getFamilyName() {
        return "Generate SELECT from table schema";
    }

    @Override
    public @Nullable Presentation getPresentation(@NotNull ActionContext context) {
        return generation(context)
                .map(g -> Presentation.of("Generate SELECT from schema of " + g.schema().target().getName()))
                .orElse(null);
    }

    @Override
    public @NotNull ModCommand perform(@NotNull ActionContext context) {
        Optional<Generation> generation = generation(context);
        if (generation.isEmpty()) {
            return ModCommand.nop();
        }
        SqlxSqlBlock block = generation.get().block();
        TestBlockSchema schema = generation.get().schema();
        String select = TestSelectGenerator.select(schema.columns(), TestBlockBody.indentOf(schema.kind()));
        PsiFile hostFile = block.getContainingFile();
        TextRange body = bodyRange(block);
        TestBlockBody.Replacement replacement =
                TestBlockBody.replacement(body.substring(hostFile.getText()), schema.kind(), select);
        TextRange range = replacement.range().shiftRight(body.getStartOffset());
        return ModCommand.psiUpdate(hostFile, (writable, updater) -> {
            Document document = writable.getFileDocument();
            document.replaceString(range.getStartOffset(), range.getEndOffset(), replacement.text());
        });
    }

    private static TextRange bodyRange(@NotNull SqlxSqlBlock block) {
        if (block.getParent() instanceof SqlxInputBlock input) {
            ASTNode open = input.getNode().findChildByType(SharedTokenTypes.LBRACE);
            ASTNode close = input.getNode().findChildByType(SharedTokenTypes.RBRACE);
            if (open != null && close != null) {
                return new TextRange(open.getTextRange().getEndOffset(), close.getStartOffset());
            }
        }
        return block.getTextRange();
    }

    private static Optional<Generation> generation(@NotNull ActionContext context) {
        PsiElement leaf = context.findLeaf();
        if (leaf == null) {
            leaf = context.findLeafOnTheLeft();
        }
        if (leaf == null) {
            return Optional.empty();
        }
        TestSchemaResolver resolver = TestSchemaResolver.getInstance(context.project());
        return resolver.blockAt(leaf).flatMap(block -> resolver.resolve(block).map(schema -> new Generation(block, schema)));
    }

    private record Generation(@NotNull SqlxSqlBlock block, @NotNull TestBlockSchema schema) {
    }
}
