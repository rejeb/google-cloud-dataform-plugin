/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
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
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class DataformJsFileIndex {

    public record IncludeExport(String fileName, String exportName, boolean isFunction, PsiFile sourceFile) {
    }

    @NotNull
    public static List<PsiFile> findDataformJsFiles(@NotNull Project project) {
        PsiManager psiManager = PsiManager.getInstance(project);
        Collection<VirtualFile> jsFiles = FileTypeIndex.getFiles(
                JavaScriptFileType.INSTANCE,
                GlobalSearchScope.projectScope(project)
        );
        return jsFiles.stream()
                .filter(DataformJsFileIndex::isDataformJsFile)
                .collect(Collectors.groupingBy(VirtualFile::getNameWithoutExtension))
                .values()
                .stream()
                .filter(group -> group.size() == 1)
                .map(group -> psiManager.findFile(group.getFirst()))
                .toList();
    }

    /**
     * Every include file of the project, whether or not another JavaScript file shares its name.
     * {@link #findDataformJsFiles} drops both files of such a pair, which is right for resolving an
     * include by name and wrong for a caller that has to search the text of them all.
     */
    @NotNull
    public static List<PsiFile> findAllIncludeFiles(@NotNull Project project) {
        return psiFilesMatching(project, DataformJsFileIndex::isDataformJsFile);
    }

    @NotNull
    private static List<PsiFile> psiFilesMatching(@NotNull Project project,
                                                  @NotNull Predicate<VirtualFile> predicate) {
        PsiManager psiManager = PsiManager.getInstance(project);
        return FileTypeIndex.getFiles(JavaScriptFileType.INSTANCE, GlobalSearchScope.projectScope(project))
                .stream()
                .filter(predicate)
                .map(psiManager::findFile)
                .filter(Objects::nonNull)
                .toList();
    }

    public static boolean isDataformJsFile(@NotNull VirtualFile file) {
        if (!"js".equals(file.getExtension())) {
            return false;
        }
        String normalizedPath = file.getPath().replace('\\', '/');
        return normalizedPath.contains("/includes/");
    }

    private static final Key<CachedValue<Map<String, List<IncludeExport>>>> EXPORTS =
            Key.create("dataform.include.exports");

    /**
     * The exports of every include file, by file name. Computed once per PSI or file-structure
     * change: completion and reference contributors ask for this on every keystroke, and walking
     * the PSI of every include each time is what made completion slow on large projects.
     */
    @NotNull
    public static Map<String, List<IncludeExport>> getAllExports(@NotNull Project project) {
        return CachedValuesManager.getManager(project).getCachedValue(project, EXPORTS, () ->
                CachedValueProvider.Result.create(computeAllExports(project),
                        PsiModificationTracker.MODIFICATION_COUNT,
                        VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS), false);
    }

    @NotNull
    private static Map<String, List<IncludeExport>> computeAllExports(@NotNull Project project) {
        Map<String, List<IncludeExport>> exportsByFile = new HashMap<>();
        for (PsiFile psiFile : findDataformJsFiles(project)) {
            if (!(psiFile instanceof JSFile jsFile)) {
                continue;
            }
            VirtualFile vFile = jsFile.getVirtualFile();
            if (vFile == null) {
                continue;
            }
            String fileName = vFile.getNameWithoutExtension();
            List<IncludeExport> exports = extractExportsFromFile(jsFile, fileName);
            if (!exports.isEmpty()) {
                exportsByFile.put(fileName, List.copyOf(exports));
            }
        }
        return Collections.unmodifiableMap(exportsByFile);
    }

    @NotNull
    private static List<IncludeExport> extractExportsFromFile(@NotNull JSFile jsFile, @NotNull String fileName) {
        List<IncludeExport> exports = new ArrayList<>();
        Set<String> functionNames = null;
        for (JSAssignmentExpression assignment : PsiTreeUtil.findChildrenOfType(jsFile, JSAssignmentExpression.class)) {
            JSExpression lhs = assignment.getLOperand();
            if (lhs == null || !"module.exports".equals(lhs.getText())) {
                continue;
            }
            if (!(assignment.getROperand() instanceof JSObjectLiteralExpression objLiteral)) {
                continue;
            }
            for (JSProperty property : objLiteral.getProperties()) {
                String propName = property.getName();
                if (propName == null) {
                    continue;
                }
                if (functionNames == null) {
                    functionNames = functionNamesOf(jsFile);
                }
                boolean isFunction = isExportFunction(property, functionNames);
                exports.add(new IncludeExport(fileName, propName, isFunction, jsFile));
            }
        }
        return exports;
    }

    /**
     * The names bound to a function in the file: declared functions and variables initialized with
     * a function expression. Collected once per file rather than once per exported property.
     */
    @NotNull
    private static Set<String> functionNamesOf(@NotNull JSFile jsFile) {
        Set<String> names = new HashSet<>();
        for (JSFunction function : PsiTreeUtil.findChildrenOfType(jsFile, JSFunction.class)) {
            if (function.getName() != null) {
                names.add(function.getName());
            }
        }
        for (JSVariable variable : PsiTreeUtil.findChildrenOfType(jsFile, JSVariable.class)) {
            if (variable.getName() != null && variable.getInitializer() instanceof JSFunctionExpression) {
                names.add(variable.getName());
            }
        }
        return names;
    }

    /**
     * Whether an exported property is a function, for both the shorthand ({@code formatDate}) and
     * the explicit ({@code formatDate: formatDate}) syntax.
     */
    private static boolean isExportFunction(@NotNull JSProperty property, @NotNull Set<String> functionNames) {
        JSExpression value = property.getValue();
        if (value instanceof JSFunctionExpression) {
            return true;
        }
        String referenceName = null;
        if (value instanceof JSReferenceExpression reference) {
            referenceName = reference.getReferenceName();
        } else if (property.getName() != null && value == null) {
            referenceName = property.getName();
        }
        return referenceName != null && functionNames.contains(referenceName);
    }
}
