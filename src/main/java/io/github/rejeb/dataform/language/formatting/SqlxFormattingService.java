package io.github.rejeb.dataform.language.formatting;

import com.intellij.formatting.service.AsyncDocumentFormattingService;
import com.intellij.formatting.service.AsyncFormattingRequest;
import com.intellij.lang.javascript.JavascriptLanguage;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiFile;
import com.intellij.sql.psi.SqlLanguage;
import io.github.rejeb.dataform.language.SqlxLanguage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.Set;

public class SqlxFormattingService extends AsyncDocumentFormattingService {

    @Override
    protected @Nullable FormattingTask createFormattingTask(@NotNull AsyncFormattingRequest formattingRequest) {

        return new SqlxFormattingTask(formattingRequest);
    }

    @Override
    protected @NotNull String getNotificationGroupId() {
        return "Dataform.Notifications";
    }

    @Override
    protected @NotNull @NlsSafe String getName() {
        return "SQLX formatting service";
    }

    @Override
    public @NotNull Set<Feature> getFeatures() {
        return Set.of(Feature.FORMAT_FRAGMENTS);
    }

    @Override
    protected Duration getTimeout() {
        return Duration.ofSeconds(5);
    }

    @Override
    protected boolean needToUpdate() {
        return false;
    }

    @Override
    public boolean canFormat(@NotNull PsiFile file) {
        if (file.getLanguage().is(SqlxLanguage.INSTANCE)) {
            return true;
        } else if (file.getLanguage().is(JavascriptLanguage.INSTANCE) || file.getLanguage().isKindOf(SqlLanguage.INSTANCE)) {
            return file.getContext() != null &&
                    file.getContext().getContainingFile() != null &&
                    file.getContext().getContainingFile().getLanguage().is(SqlxLanguage.INSTANCE);
        } else {
            return false;
        }
    }
}
