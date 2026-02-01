/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.rejeb.dataform.language.index;

import com.intellij.lang.javascript.JavaScriptFileType;
import com.intellij.lang.javascript.psi.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class DataformJsFileIndex {

    public record IncludeExport(String fileName, String exportName, boolean isFunction, PsiFile sourceFile) {
    }

    @NotNull
    public static List<PsiFile> findDataformJsFiles(@NotNull Project project) {
        List<PsiFile> result = new ArrayList<>();
        Collection<VirtualFile> jsFiles = FileTypeIndex.getFiles(
                JavaScriptFileType.INSTANCE,
                GlobalSearchScope.projectScope(project)
        );

        if (jsFiles.isEmpty()) {
            return result;
        }

        PsiManager psiManager = PsiManager.getInstance(project);

        for (VirtualFile file : jsFiles) {
            if (isDataformJsFile(file)) {
                PsiFile psiFile = psiManager.findFile(file);
                if (psiFile != null) {
                    result.add(psiFile);
                }
            }
        }

        return result;
    }

    public static boolean isDataformJsFile(@NotNull VirtualFile file) {
        if (!"js".equals(file.getExtension())) {
            return false;
        }
        String normalizedPath = file.getPath().replace('\\', '/');
        return normalizedPath.contains("/includes/");
    }

    @NotNull
    public static Map<String, List<IncludeExport>> getAllExports(@NotNull Project project) {
        Map<String, List<IncludeExport>> exportsByFile = new HashMap<>();
        List<PsiFile> jsFiles = findDataformJsFiles(project);

        for (PsiFile psiFile : jsFiles) {

            if (!(psiFile instanceof JSFile)) {
                continue;
            }

            JSFile jsFile = (JSFile) psiFile;
            VirtualFile vFile = jsFile.getVirtualFile();
            if (vFile == null) {
                continue;
            }

            String fileName = vFile.getNameWithoutExtension();

            List<IncludeExport> exports = extractExportsFromFile(jsFile, fileName);

            if (!exports.isEmpty()) {
                exportsByFile.put(fileName, exports);
            }
        }

        return exportsByFile;
    }

    @NotNull
    private static List<IncludeExport> extractExportsFromFile(@NotNull JSFile jsFile, @NotNull String fileName) {
        List<IncludeExport> exports = new ArrayList<>();
        Collection<JSAssignmentExpression> assignments =
                PsiTreeUtil.findChildrenOfType(jsFile, JSAssignmentExpression.class);

        for (JSAssignmentExpression assignment : assignments) {
            JSExpression lhs = assignment.getLOperand();
            if (lhs != null && "module.exports".equals(lhs.getText())) {
                JSExpression rhs = assignment.getROperand();

                if (rhs instanceof JSObjectLiteralExpression) {
                    JSObjectLiteralExpression objLiteral = (JSObjectLiteralExpression) rhs;

                    for (JSProperty property : objLiteral.getProperties()) {
                        String propName = property.getName();
                        if (propName != null) {

                            boolean isFunction = isExportFunction(property, jsFile);


                            exports.add(new IncludeExport(fileName, propName, isFunction, jsFile));
                        }
                    }
                }
            }
        }

        return exports;
    }

    /**
     * Vérifie si une propriété d'export est une fonction
     * Gère à la fois la syntaxe shorthand (formatDate) et explicite (formatDate: formatDate)
     */
    private static boolean isExportFunction(@NotNull JSProperty property, @NotNull JSFile jsFile) {
        JSExpression value = property.getValue();


        if (value instanceof JSFunctionExpression) {
            return true;
        }


        String referenceName = null;

        if (value instanceof JSReferenceExpression) {

            referenceName = ((JSReferenceExpression) value).getReferencedName();
        } else if (property.getName() != null && value == null) {

            referenceName = property.getName();
        }

        if (referenceName == null) {
            return false;
        }


        Collection<JSFunction> functions = PsiTreeUtil.findChildrenOfType(jsFile, JSFunction.class);
        for (JSFunction function : functions) {
            if (referenceName.equals(function.getName())) {
                return true;
            }
        }


        Collection<JSVariable> variables = PsiTreeUtil.findChildrenOfType(jsFile, JSVariable.class);
        for (JSVariable variable : variables) {
            if (referenceName.equals(variable.getName())) {
                JSExpression initializer = variable.getInitializer();
                boolean isFunctionVar = initializer instanceof JSFunctionExpression;
                return isFunctionVar;
            }
        }

        return false;
    }
}
