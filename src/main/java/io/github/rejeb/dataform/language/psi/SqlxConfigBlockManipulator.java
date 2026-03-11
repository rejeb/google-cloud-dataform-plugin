package io.github.rejeb.dataform.language.psi;

import com.intellij.json.json5.Json5Language;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.AbstractElementManipulator;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class SqlxConfigBlockManipulator extends AbstractElementManipulator<SqlxConfigBlock> {

    @Override
    public SqlxConfigBlock handleContentChange(@NotNull SqlxConfigBlock element,
                                               @NotNull TextRange range,
                                               @NotNull String newContent)
            throws IncorrectOperationException {
        String oldText = element.getText();
        String newText = oldText.substring(0, range.getStartOffset())
                + newContent
                + oldText.substring(range.getEndOffset());

        String fakeFile = "config " + newText;

        PsiFile fileFromText = PsiFileFactory.getInstance(element.getProject())
                .createFileFromText("dummy.json", Json5Language.INSTANCE, fakeFile);

        SqlxConfigBlock newElement = PsiTreeUtil.findChildOfType(fileFromText, SqlxConfigBlock.class);
        if (newElement == null) return element;
        return (SqlxConfigBlock) element.replace(newElement);
    }

    @Override
    public @NotNull TextRange getRangeInElement(@NotNull SqlxConfigBlock element) {
        return TextRange.from(0, element.getTextLength());
    }
}
