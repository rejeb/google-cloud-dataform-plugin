package io.github.rejeb.dataform.language.compilation.model;

import java.util.ArrayList;
import java.util.List;

public class GraphErrors {
    List<CompilationError> compilationErrors = new ArrayList<>();

    public List<CompilationError> getCompilationErrors() {
        return compilationErrors;
    }

    public void setCompilationErrors(List<CompilationError> compilationErrors) {
        this.compilationErrors = compilationErrors;
    }
}
