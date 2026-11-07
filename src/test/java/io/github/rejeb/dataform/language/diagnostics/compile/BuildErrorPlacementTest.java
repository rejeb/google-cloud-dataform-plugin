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
package io.github.rejeb.dataform.language.diagnostics.compile;

import com.google.gson.JsonObject;
import com.google.gson.Gson;
import com.intellij.openapi.application.ReadAction;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.compilation.PlacedError;
import io.github.rejeb.dataform.language.compilation.model.CompilationError;

public class BuildErrorPlacementTest extends BasePlatformTestCase {

    private static final String ROOT = "/tmp/copy/";

    private static CompilationError error(String fileName, String message, String stack) {
        JsonObject json = new JsonObject();
        json.addProperty("actionName", "p.d.placed");
        json.addProperty("fileName", fileName);
        json.addProperty("message", message);
        json.addProperty("stack", stack);
        return new Gson().fromJson(json, CompilationError.class);
    }

    private static String frame(String function, String path, int line, int column) {
        return "    at " + function + " (" + ROOT + path + ":" + line + ":" + column + ")";
    }

    private PlacedError place(CompilationError error) {
        return ReadAction.nonBlocking(() -> BuildErrorPlacement.place(getProject(), error)).executeSynchronously();
    }

    public void testAnUndefinedFunctionIsPlacedOnItsName() {
        String text = "config { type: \"table\" }\nSELECT ${undefinedFn()} AS c\n";
        myFixture.addFileToProject("definitions/placed.sqlx", text);

        PlacedError placed = place(error("definitions/placed.sqlx", "undefinedFn is not defined",
                "ReferenceError: undefinedFn is not defined\n"
                        + frame("Object.sqlContextable", "definitions/placed.sqlx", 19, 13)));

        assertNotNull(placed);
        assertEquals("definitions/placed.sqlx", placed.path());
        assertEquals(1, placed.line());
        assertEquals("SELECT ${".length(), placed.column());
        assertEquals("undefinedFn".length(), placed.length());
        assertEquals(text, placed.fileText());
        assertNull(placed.thrownInPath());
        assertNotNull(placed.hint());
        assertTrue(placed.hint(), placed.hint().contains("undefinedFn"));
    }

    public void testAnErrorRaisedInAnIncludeIsPlacedAtTheCallWithWhereToFixIt() {
        myFixture.addFileToProject("definitions/caller.sqlx",
                "config { type: \"table\" }\nSELECT ${util.boom()} AS c\n");
        myFixture.addFileToProject("includes/util.js",
                "function boom() {\n  return missing.value;\n}\nmodule.exports = { boom };\n");

        PlacedError placed = place(error("definitions/caller.sqlx", "missing is not defined",
                "ReferenceError: missing is not defined\n"
                        + frame("boom", "includes/util.js", 2, 10) + "\n"
                        + frame("Object.sqlContextable", "definitions/caller.sqlx", 19, 13)));

        assertNotNull(placed);
        assertNull(placed.thrownInPath());
        assertEquals(1, placed.line());
        assertEquals("SELECT ${util.".length(), placed.column());
        assertEquals("Fix it in includes/util.js line 2.", placed.hint());
    }

    public void testAnErrorWhoseCallIsNotFoundIsPlacedInTheInclude() {
        myFixture.addFileToProject("definitions/caller.sqlx",
                "config { type: \"table\" }\nSELECT ${util.outer()} AS c\n");
        String include = "function inner() {\n  return missing.value;\n}\nfunction outer() {\n  return inner();\n}\n"
                + "module.exports = { outer };\n";
        myFixture.addFileToProject("includes/util.js", include);

        PlacedError placed = place(error("definitions/caller.sqlx", "missing is not defined",
                "ReferenceError: missing is not defined\n"
                        + frame("inner", "includes/util.js", 2, 10) + "\n"
                        + frame("Object.sqlContextable", "definitions/caller.sqlx", 19, 13)));

        assertNotNull(placed);
        assertEquals("definitions/caller.sqlx", placed.path());
        assertEquals("includes/util.js", placed.thrownInPath());
        assertEquals(1, placed.line());
        assertEquals("  return ".length(), placed.column());
        assertEquals(include, placed.fileText());
    }

    public void testAMissingFileIsNotPlaced() {
        assertNull(place(error("definitions/missing.sqlx", "undefinedFn is not defined",
                "ReferenceError: undefinedFn is not defined\n"
                        + frame("Object.sqlContextable", "definitions/missing.sqlx", 19, 13))));
    }
}
