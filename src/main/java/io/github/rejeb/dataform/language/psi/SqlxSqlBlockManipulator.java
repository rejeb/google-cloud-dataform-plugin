package io.github.rejeb.dataform.language.psi;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.AbstractElementManipulator;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import io.github.rejeb.dataform.language.SqlxLanguage;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

public class SqlxSqlBlockManipulator extends AbstractElementManipulator<SqlxSqlBlock> {

    @Override
    public SqlxSqlBlock handleContentChange(@NotNull SqlxSqlBlock element,
                                            @NotNull TextRange range,
                                            @NotNull String newContent)
            throws IncorrectOperationException {
        // Créer un nouvel élément avec le contenu modifié
        String oldText = element.getText();
        String newText = oldText.substring(0, range.getStartOffset())
                + newContent
                + oldText.substring(range.getEndOffset());
        PsiFile fileFromText = PsiFileFactory.getInstance(element.getProject())
                .createFileFromText("dummy.sqlx",
                        SqlxLanguage.INSTANCE,
                        newText);
        // Utiliser votre factory pour créer le nouvel élément
        // et remplacer l'ancien
        SqlxSqlBlock newElement = PsiTreeUtil.findChildOfType(fileFromText, SqlxSqlBlock.class);
        return (SqlxSqlBlock) element.replace(newElement);
    }

    @Override
    public @NonNull TextRange getRangeInElement(@NotNull SqlxSqlBlock element) {
        // Retourner la plage de texte qui peut être modifiée
        // Par exemple, tout le contenu de l'élément
        return TextRange.from(0, element.getTextLength());
    }
}