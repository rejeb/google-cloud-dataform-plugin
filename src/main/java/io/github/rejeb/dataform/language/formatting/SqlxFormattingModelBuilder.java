package io.github.rejeb.dataform.language.formatting;

import com.intellij.formatting.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.common.DefaultInjectedLanguageBlockBuilder;
import com.intellij.psi.formatter.common.InjectedLanguageBlockBuilder;
import com.intellij.psi.tree.TokenSet;
import io.github.rejeb.dataform.language.SqlxLanguage;
import io.github.rejeb.dataform.language.psi.SharedTokenTypes;
import io.github.rejeb.dataform.language.psi.SqlxPsiElement;
import org.jetbrains.annotations.NotNull;

public class SqlxFormattingModelBuilder implements CustomFormattingModelBuilder {
    @Override
    public boolean isEngagedToFormat(PsiElement context) {
        return context instanceof SqlxPsiElement;
    }

    @Override
    public @NotNull FormattingModel createModel(@NotNull FormattingContext formattingContext) {
        final CodeStyleSettings codeStyleSettings = formattingContext.getCodeStyleSettings();
        return FormattingModelProvider
                .createFormattingModelForPsiFile(formattingContext.getContainingFile(),
                        new SqlxFormattingBlock(formattingContext.getNode(),
                                createSpaceBuilder(codeStyleSettings),
                                injectionBuilder(codeStyleSettings),
                                Wrap.createWrap(WrapType.NONE, false)
                        ),
                        codeStyleSettings);
    }

    private static SpacingBuilder createSpaceBuilder(CodeStyleSettings settings) {
        return new SpacingBuilder(settings, SqlxLanguage.INSTANCE)
                .between(TokenSet.create(SharedTokenTypes.CONFIG_KEYWORD,
                        SharedTokenTypes.JS_KEYWORD,
                        SharedTokenTypes.POST_OPERATIONS_KEYWORD,
                        SharedTokenTypes.PRE_OPERATIONS_KEYWORD), SharedTokenTypes.LBRACE)
                .spaces(1)
                .between(SharedTokenTypes.LBRACE, SharedTokenTypes.SQL_CONTENT)
                .lineBreakInCode();
    }

    private static InjectedLanguageBlockBuilder injectionBuilder(CodeStyleSettings settings) {
        return new DefaultInjectedLanguageBlockBuilder(settings);
    }
}
