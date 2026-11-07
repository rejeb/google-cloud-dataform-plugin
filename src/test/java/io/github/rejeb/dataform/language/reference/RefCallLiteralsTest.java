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
package io.github.rejeb.dataform.language.reference;

import com.intellij.lang.javascript.psi.JSCallExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public class RefCallLiteralsTest extends BasePlatformTestCase {

    private JSCallExpression callIn(String code) {
        PsiFile file = myFixture.configureByText("a.js", code);
        JSCallExpression call = PsiTreeUtil.findChildOfType(file, JSCallExpression.class);
        assertNotNull(code, call);
        return call;
    }

    public void testRefAndResolveAreRefCalls() {
        assertTrue(RefCallLiterals.isRefCall(callIn("ref('a');")));
        assertTrue(RefCallLiterals.isRefCall(callIn("resolve('a');")));
    }

    public void testACallStillMissingItsNameIsNoRefCall() {
        assertFalse(RefCallLiterals.isRefCall(callIn("a.();")));
    }
}
