package io.github.rejeb.dataform.language.gcp.execution.workflow.runconfig;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.icons.AllIcons;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import io.github.rejeb.dataform.language.psi.SqlxConfigBlock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static io.github.rejeb.dataform.language.util.Utils.isActionFile;

public class SqlxEditorGutterProvider implements LineMarkerProvider {

    @Override
    public @Nullable LineMarkerInfo<?> getLineMarkerInfo(@NotNull PsiElement element) {
        if (!isActionFile(element.getContainingFile().getVirtualFile())) return null;
        if (PsiTreeUtil.getParentOfType(element, SqlxConfigBlock.class) == null) return null;
        if (!element.getText().equals("tags")) return null;

        PsiFile containingFile = InjectedLanguageManager.getInstance(element.getProject())
                .getTopLevelFile(element);
        if (!isActionFile(containingFile.getVirtualFile())) return null;

        return new LineMarkerInfo<>(
                element,
                element.getTextRange(),
                AllIcons.Actions.Execute,
                e -> "Run " + containingFile.getVirtualFile().getNameWithoutExtension(),
                (mouseEvent, psiElement) -> {
                    if (containingFile.getVirtualFile() == null) return;
                    SqlxRunOptionsPopup.show(containingFile.getProject(), (deps, dependants, fullRefresh) ->
                            RunSqlxHelper.launchFromTags(containingFile.getProject(), containingFile.getVirtualFile(),
                                    deps, dependants, fullRefresh)
                    );
                },
                GutterIconRenderer.Alignment.LEFT,
                () -> "Run " + containingFile.getName()
        );
    }
}