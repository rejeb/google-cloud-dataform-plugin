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
package io.github.rejeb.dataform.language.gcp.clone;

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class PendingRepositoryLinksImpl implements PendingRepositoryLinks {

    private final Map<String, RepositoryLink> links = new ConcurrentHashMap<>();

    @Override
    public void register(@NotNull Path directory, @NotNull RepositoryLink link) {
        links.put(directory.toAbsolutePath().normalize().toString(), link);
    }

    @Override
    public @Nullable RepositoryLink take(@NotNull String projectDirectory) {
        for (String directory : links.keySet()) {
            if (FileUtil.pathsEqual(directory, projectDirectory)) {
                return links.remove(directory);
            }
        }
        return null;
    }
}
