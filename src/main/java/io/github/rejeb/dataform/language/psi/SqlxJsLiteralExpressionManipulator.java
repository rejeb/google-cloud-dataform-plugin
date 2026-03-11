package io.github.rejeb.dataform.language.psi;

import com.intellij.lang.javascript.JavascriptLanguage;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.AbstractElementManipulator;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class SqlxJsLiteralExpressionManipulator extends AbstractElementManipulator<SqlxJsLiteralExpression> {

    @Override
    public SqlxJsLiteralExpression handleContentChange(@NotNull SqlxJsLiteralExpression element,
                                                       @NotNull TextRange range,
                                                       @NotNull String newContent)
            throws IncorrectOperationException {
        String oldText = element.getText();
        String newText = oldText.substring(0, range.getStartOffset())
                + newContent
                + oldText.substring(range.getEndOffset());

        String fakeFile = "const elem= " + newText;

        PsiFile fileFromText = PsiFileFactory.getInstance(element.getProject())
                .createFileFromText("dummy.js", JavascriptLanguage.INSTANCE, fakeFile);

        SqlxJsLiteralExpression newElement =
                PsiTreeUtil.findChildOfType(fileFromText, SqlxJsLiteralExpression.class);
        if (newElement == null) return element;
        return (SqlxJsLiteralExpression) element.replace(newElement);
    }

    @Override
    public @NotNull TextRange getRangeInElement(@NotNull SqlxJsLiteralExpression element) {
        return TextRange.from(0, element.getTextLength());
    }
}
