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
package io.github.rejeb.dataform.language.compilation.model;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * On Windows the Dataform CLI writes the file name of every action with backslashes. Each compiled
 * model must hand it out with slashes and match the IDE path of its file.
 */
public class WindowsFileNameTest {

    private static final String WINDOWS_NAME = "definitions\\staging\\orders.sqlx";
    private static final String IDE_PATH = "C:/work/project/definitions/staging/orders.sqlx";
    private static final String OTHER_PATH = "C:/work/project/definitions/staging/old_orders.sqlx";

    private static <T> T withFileName(T model) {
        try {
            Field field = model.getClass().getDeclaredField("fileName");
            field.setAccessible(true);
            field.set(model, WINDOWS_NAME);
            return model;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void tableHandsOutAndMatchesItsFileName() {
        CompiledTable table = withFileName(new CompiledTable());
        assertEquals("definitions/staging/orders.sqlx", table.getFileName());
        assertTrue(table.matchFileName(IDE_PATH));
        assertFalse(table.matchFileName(OTHER_PATH));
    }

    @Test
    void operationHandsOutAndMatchesItsFileName() {
        CompiledOperation operation = withFileName(new CompiledOperation());
        assertEquals("definitions/staging/orders.sqlx", operation.getFileName());
        assertTrue(operation.matchFileName(IDE_PATH));
        assertFalse(operation.matchFileName(OTHER_PATH));
    }

    @Test
    void assertionHandsOutAndMatchesItsFileName() {
        CompiledAssertion assertion = withFileName(new CompiledAssertion());
        assertEquals("definitions/staging/orders.sqlx", assertion.getFileName());
        assertTrue(assertion.matchFileName(IDE_PATH));
        assertFalse(assertion.matchFileName(OTHER_PATH));
    }

    @Test
    void declarationHandsOutAndMatchesItsFileName() {
        Declaration declaration = withFileName(new Declaration());
        assertEquals("definitions/staging/orders.sqlx", declaration.getFileName());
        assertTrue(declaration.matchFileName(IDE_PATH));
        assertFalse(declaration.matchFileName(OTHER_PATH));
    }

    @Test
    void compilationErrorHandsOutAndMatchesItsFileName() {
        CompilationError error = withFileName(new CompilationError());
        assertEquals("definitions/staging/orders.sqlx", error.getFileName());
        assertTrue(error.matchFileName(IDE_PATH));
        assertFalse(error.matchFileName(OTHER_PATH));
    }

    @Test
    void anActionWithoutFileNameMatchesNothing() {
        assertFalse(new Declaration().matchFileName(IDE_PATH));
        assertFalse(new CompiledTable().matchFileName(IDE_PATH));
    }
}
