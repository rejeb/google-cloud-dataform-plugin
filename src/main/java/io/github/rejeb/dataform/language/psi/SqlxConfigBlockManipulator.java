package io.github.rejeb.dataform.language.psi;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.AbstractElementManipulator;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class SqlxConfigBlockManipulator extends AbstractElementManipulator<SqlxConfigBlock> {

    @Override
    public SqlxConfigBlock handleContentChange(@NotNull SqlxConfigBlock element,
                                               @NotNull TextRange range,
                                               @NotNull String newContent)
            throws IncorrectOperationException {

        return (SqlxConfigBlock) element.replace(
                element.getContainingFile().copy()
        );
    }

    @Override
    public @NotNull TextRange getRangeInElement(@NotNull SqlxConfigBlock element) {
        return TextRange.from(0, element.getTextLength());
    }
}
