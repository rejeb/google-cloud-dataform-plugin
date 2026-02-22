package io.github.rejeb.dataform.language.compilation.model;

public class CompilationError {
    private String fileName;
    private String message;
    private String stack;

    public String getFileName() {
        return fileName;
    }

    public String getMessage() {
        return message;
    }

    public String getStack() {
        return stack;
    }
}
