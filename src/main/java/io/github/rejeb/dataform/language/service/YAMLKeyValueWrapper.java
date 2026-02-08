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
package io.github.rejeb.dataform.language.service;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.eclipse.lsp4j.jsonrpc.validation.NonNull;
import org.jetbrains.yaml.psi.impl.YAMLKeyValueImpl;

import java.util.Optional;

public class YAMLKeyValueWrapper extends YAMLKeyValueImpl {
    private final PsiElement delegate;
    private final PsiElement target;

    YAMLKeyValueWrapper(PsiElement delegate, PsiElement target) {
        super(Optional.ofNullable(target).orElse(delegate).getNode());
        this.delegate = delegate;
        this.target = target;
    }

    @Override
    public @NonNull PsiElement getNavigationElement() {
        return target;
    }

    @Override
    public PsiElement getParent() {
        return delegate.getParent();
    }

    @Override
    public String getText() {
        return delegate.getText();
    }

    @Override
    public TextRange getTextRange() {
        return delegate.getTextRange();
    }

    @Override
    public @NonNull ASTNode getNode() {
        return delegate.getNode();
    }

}