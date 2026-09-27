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
package io.github.rejeb.dataform.language.unittest;

import com.intellij.lang.javascript.psi.JSExpression;
import com.intellij.lang.javascript.psi.JSLiteralExpression;
import com.intellij.lang.javascript.psi.JSObjectLiteralExpression;
import com.intellij.lang.javascript.psi.JSProperty;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import io.github.rejeb.dataform.language.compilation.model.ActionReference;
import io.github.rejeb.dataform.language.injection.InjectedFiles;
import io.github.rejeb.dataform.language.psi.SqlxConfigBlock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

public final class SqlxUnitTests {

    public static final String TEST_TYPE = "test";
    private static final String TYPE_KEY = "type";
    private static final String DATASET_KEY = "dataset";
    private static final String NAME_KEY = "name";
    private static final Pattern TEST_TYPE_DECLARATION =
            Pattern.compile("(?<![\\w$])type\\s*:\\s*([\"'`])" + TEST_TYPE + "\\1");

    private SqlxUnitTests() {
    }

    /**
     * Tells whether the given config object literal declares a unit test.
     */
    public static boolean isUnitTestConfig(@Nullable JSObjectLiteralExpression config) {
        return config != null && TEST_TYPE.equals(stringValue(config, TYPE_KEY));
    }

    /**
     * Tells whether the config block of the given SQLX file declares a unit test. Reads only the host
     * text of the config block, never injected PSI, so it is safe on the EDT under a read action.
     */
    public static boolean isUnitTestFile(@NotNull PsiFile sqlxFile) {
        SqlxConfigBlock config = PsiTreeUtil.findChildOfType(sqlxFile, SqlxConfigBlock.class);
        return config != null && TEST_TYPE_DECLARATION.matcher(config.getText()).find();
    }

    /**
     * Returns the name of the action under test, given by {@code dataset} as a string or as the
     * {@code name} of a target object. Reads injected PSI: call it under a read action, off the EDT.
     */
    public static Optional<String> testedDatasetName(@NotNull PsiFile sqlxFile) {
        return configObject(sqlxFile)
                .filter(SqlxUnitTests::isUnitTestConfig)
                .map(config -> config.findProperty(DATASET_KEY))
                .map(dataset -> dataset.getValue() instanceof JSObjectLiteralExpression target
                        ? stringValue(target, NAME_KEY)
                        : literalString(dataset.getValue()));
    }

    /**
     * Returns the action a literal of a unit test config names: the value of {@code dataset}, or the
     * {@code name} of a {@code dataset} target object together with its {@code schema} and
     * {@code database}. Empty for any other literal.
     */
    public static Optional<ActionReference> testedActionNamedBy(@NotNull JSLiteralExpression literal) {
        if (!literal.isQuotedLiteral() || !(literal.getValue() instanceof String name) || name.isBlank()
                || !(literal.getParent() instanceof JSProperty property)
                || !(property.getParent() instanceof JSObjectLiteralExpression owner)) {
            return Optional.empty();
        }
        if (DATASET_KEY.equals(property.getName()) && isUnitTestConfig(owner) && isTopLevel(owner)) {
            return Optional.of(ActionReference.named(name));
        }
        if (NAME_KEY.equals(property.getName())
                && owner.getParent() instanceof JSProperty dataset
                && DATASET_KEY.equals(dataset.getName())
                && dataset.getParent() instanceof JSObjectLiteralExpression config
                && isUnitTestConfig(config) && isTopLevel(config)) {
            return Optional.of(new ActionReference(stringValue(owner, "database"), stringValue(owner, "schema"), name));
        }
        return Optional.empty();
    }

    /**
     * Tells whether a literal is the string value of the {@code dataset} key of a unit test config.
     */
    public static boolean isTestedDatasetString(@NotNull JSLiteralExpression literal) {
        return literal.isQuotedLiteral()
                && literal.getParent() instanceof JSProperty property
                && DATASET_KEY.equals(property.getName())
                && property.getParent() instanceof JSObjectLiteralExpression config
                && isTopLevel(config)
                && isUnitTestConfig(config);
    }

    private static boolean isTopLevel(@NotNull JSObjectLiteralExpression object) {
        return PsiTreeUtil.getParentOfType(object, JSObjectLiteralExpression.class, true) == null;
    }

    /**
     * Returns the object literal injected into the config block of the given SQLX file.
     */
    public static Optional<JSObjectLiteralExpression> configObject(@NotNull PsiFile sqlxFile) {
        return InjectedFiles.inside(sqlxFile, SqlxConfigBlock.class).stream()
                .map(file -> PsiTreeUtil.findChildOfType(file, JSObjectLiteralExpression.class))
                .filter(Objects::nonNull)
                .findFirst();
    }

    @Nullable
    private static String stringValue(@NotNull JSObjectLiteralExpression object, @NotNull String key) {
        JSProperty property = object.findProperty(key);
        return property == null ? null : literalString(property.getValue());
    }

    @Nullable
    private static String literalString(@Nullable JSExpression value) {
        return value instanceof JSLiteralExpression literal
                && literal.getValue() instanceof String text
                && !text.isBlank() ? text : null;
    }
}
