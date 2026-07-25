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
package io.github.rejeb.dataform.language.documentation.bigquery;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class BigQueryFunctionDocServiceImpl implements BigQueryFunctionDocService {

    private static final Logger LOG = Logger.getInstance(BigQueryFunctionDocServiceImpl.class);
    private static final String RESOURCE = "/bigquery/bigquery-functions.json";

    private volatile Map<String, BigQueryFunctionDoc> myFunctions;

    @Override
    public @NotNull Optional<BigQueryFunctionDoc> find(@NotNull String name) {
        return Optional.ofNullable(functions().get(name.toUpperCase(Locale.ROOT)));
    }

    @Override
    public boolean isKnownFunction(@NotNull String name) {
        return functions().containsKey(name.toUpperCase(Locale.ROOT));
    }

    @Override
    public @NotNull Collection<BigQueryFunctionDoc> getAll() {
        return functions().values();
    }

    private Map<String, BigQueryFunctionDoc> functions() {
        Map<String, BigQueryFunctionDoc> local = myFunctions;
        if (local == null) {
            synchronized (this) {
                local = myFunctions;
                if (local == null) {
                    local = load();
                    myFunctions = local;
                }
            }
        }
        return local;
    }

    private Map<String, BigQueryFunctionDoc> load() {
        try (InputStream stream = BigQueryFunctionDocServiceImpl.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) {
                LOG.warn("BigQuery function documentation resource not found: " + RESOURCE);
                return Map.of();
            }
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                Type type = new TypeToken<List<BigQueryFunctionDoc>>() {
                }.getType();
                List<BigQueryFunctionDoc> parsed = new Gson().fromJson(reader, type);
                if (parsed == null) {
                    return Map.of();
                }
                Map<String, BigQueryFunctionDoc> result = new LinkedHashMap<>();
                for (BigQueryFunctionDoc doc : parsed) {
                    result.put(doc.name().toUpperCase(Locale.ROOT), doc);
                }
                return Map.copyOf(result);
            }
        } catch (Exception e) {
            LOG.warn("Failed to load BigQuery function documentation", e);
            return Map.of();
        }
    }
}
