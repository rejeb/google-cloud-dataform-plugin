package io.github.rejeb.dataform.language.formatting;

import com.intellij.openapi.util.TextRange;

public record BlockChange(TextRange range, String text) {
}
