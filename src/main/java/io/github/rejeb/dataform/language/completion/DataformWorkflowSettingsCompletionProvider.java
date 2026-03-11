package io.github.rejeb.dataform.language.completion;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.javascript.psi.JSFile;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import io.github.rejeb.dataform.language.psi.SqlxFile;
import io.github.rejeb.dataform.language.service.WorkflowSettingsProperty;
import io.github.rejeb.dataform.language.service.WorkflowSettingsService;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

public class DataformWorkflowSettingsCompletionProvider extends CompletionProvider<CompletionParameters> {

    @Override
    protected void addCompletions(@NotNull CompletionParameters parameters,
                                  @NotNull ProcessingContext context,
                                  @NotNull CompletionResultSet result) {

        PsiElement position = parameters.getPosition();

        PsiFile topLevelFile = InjectedLanguageManager.getInstance(position.getProject())
                .getTopLevelFile(position);

        if (!(topLevelFile instanceof SqlxFile) && !(topLevelFile instanceof JSFile)) {
            return;
        }

        String prefix = extractPrefix(position);

        WorkflowSettingsService service = WorkflowSettingsService.getInstance(position.getProject());

        Collection<String> properties = service.getPropertiesForPrefix(prefix);

        for (String property : properties) {
            LookupElementBuilder element = LookupElementBuilder.create(property)
                    .withTypeText("workflow_settings.yaml")
                    .withIcon(com.intellij.icons.AllIcons.Nodes.Variable);
            String[] parts = prefix!=null ? prefix.split("\\.") : new String[0];
            WorkflowSettingsProperty prop = findNestedProperty(service, parts, property);

            if (prop != null && prop.hasChildren()) {
                element = element.withInsertHandler((ctx, item) -> {
                    Editor editor = ctx.getEditor();
                    int offset = editor.getCaretModel().getOffset();
                    editor.getDocument().insertString(offset, ".");
                    editor.getCaretModel().moveToOffset(offset + 1);
                    AutoPopupController.getInstance(ctx.getProject())
                            .scheduleAutoPopup(editor);
                });
            }

            result.addElement(PrioritizedLookupElement.withPriority(element, Double.MAX_VALUE));
        }
    }


    private String extractPrefix(PsiElement position) {
        PsiElement prevLeaf = PsiTreeUtil.prevLeaf(position);
        List<String> ancestors = new LinkedList<>();
        while (prevLeaf != null && !prevLeaf.getText().trim().isEmpty() && !prevLeaf.getText().trim().equals("{")) {
            if (!prevLeaf.getText().equals(".")) {
                ancestors.addFirst(prevLeaf.getText());
            }
            prevLeaf = PsiTreeUtil.prevLeaf(prevLeaf);
        }
        return ancestors.isEmpty() ? null : String.join(".", ancestors);
    }


    private WorkflowSettingsProperty findNestedProperty(
            WorkflowSettingsService service, String[] parentPath, String propertyName) {
        var current = service.getWorkflowProperties();

        for (String part : parentPath) {
            WorkflowSettingsProperty prop = current.get(part);
            if (prop == null || !prop.hasChildren()) {
                return null;
            }
            current = prop.children();
        }

        return current.get(propertyName);
    }
}
