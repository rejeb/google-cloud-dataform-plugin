package io.github.rejeb.dataform.language.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.sql.dialects.bigquery.BigQueryDialect;
import org.jetbrains.annotations.NotNull;

public class Utils {

    public static String formatSql(@NotNull Project project, @NotNull String sql) {
        PsiFile file = PsiFileFactory.getInstance(project)
                .createFileFromText("temp.sql", BigQueryDialect.INSTANCE, sql);
        Runnable format = () ->
                CodeStyleManager.getInstance(project).reformat(file);
        WriteCommandAction.runWriteCommandAction(project, format);

        return file.getText();
    }

    public static void flushFiles(Project project) {
        ApplicationManager.getApplication().invokeAndWait(() -> {
            PsiDocumentManager.getInstance(project).commitAllDocuments();
            FileDocumentManager.getInstance().saveAllDocuments();
        });
    }

    public static boolean isActionFile(@NotNull VirtualFile file) {
        String normalizedPath = file.getPath().replace('\\', '/');
        return normalizedPath.contains("/definitions/") &&
                (normalizedPath.endsWith(".sqlx") || normalizedPath.endsWith(".js")) &&
                file.isWritable();
    }
}
