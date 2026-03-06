package io.github.rejeb.dataform.language.fileEditor;

import com.intellij.openapi.fileEditor.ChangePreviewLayoutAction;
import com.intellij.openapi.fileEditor.TextEditorWithPreview;

public class ShowDataformPreviewAction extends ChangePreviewLayoutAction {
    public ShowDataformPreviewAction( TextEditorWithPreview.Layout layout) {
        super(layout);
    }


}
