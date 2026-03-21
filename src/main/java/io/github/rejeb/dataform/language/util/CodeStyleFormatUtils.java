package io.github.rejeb.dataform.language.util;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.sql.dialects.bigquery.BigQueryDialect;
import org.jetbrains.annotations.NotNull;

public class CodeStyleFormatUtils {

    public static String formatSql(@NotNull Project project, @NotNull String sql) {
        PsiFile file = PsiFileFactory.getInstance(project)
                .createFileFromText("temp.sql", BigQueryDialect.INSTANCE, sql);
        Runnable format = () ->
                CodeStyleManager.getInstance(project).reformat(file);
        WriteCommandAction.runWriteCommandAction(project, format);

        return file.getText();
    }
}
