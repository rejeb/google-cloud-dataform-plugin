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
package io.github.rejeb.dataform.language.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Paths coming from Windows use backslashes. Every conversion goes through
 * {@code DataformPaths}; a hand-written separator swap elsewhere is how a feature ends up working
 * on Linux and silently failing on Windows.
 */
public class PathSeparatorGuardTest {

    private static final Path SOURCES = Path.of("src/main/java");
    private static final String HELPER = "DataformPaths.java";
    private static final Pattern SEPARATOR_SWAP =
            Pattern.compile("replace(All)?\\(\\s*(\"\\\\\\\\\"|'\\\\\\\\')\\s*,\\s*(\"/\"|'/')");

    @Test
    void separatorsAreOnlySwappedByDataformPaths() throws IOException {
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(SOURCES)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                if (file.getFileName().toString().equals(HELPER)) continue;
                List<String> lines = Files.readAllLines(file);
                for (int i = 0; i < lines.size(); i++) {
                    if (SEPARATOR_SWAP.matcher(lines.get(i)).find()) {
                        offenders.add(file + ":" + (i + 1) + "  " + lines.get(i).trim());
                    }
                }
            }
        }
        assertTrue(offenders.isEmpty(),
                "Use DataformPaths.normalize / pointsTo / findInProject instead of swapping separators by hand:\n"
                        + String.join("\n", offenders));
    }
}
