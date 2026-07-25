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
package io.github.rejeb.dataform.language.evaluation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * Builds the Node harness that reproduces the Dataform evaluation environment and the JSON payload
 * it consumes.
 */
public final class DataformEvalScriptBuilder {

    public static final String SCRIPT_NAME = "dataform-eval";

    private static final Gson GSON = new GsonBuilder().serializeNulls().create();

    private static final String SCRIPT = """
            const fs = require('fs');
            const vm = require('vm');

            const payload = JSON.parse(fs.readFileSync(0, 'utf8'));
            const nodePaths = payload.nodePaths || [];
            for (const path of nodePaths) {
              if (module.paths.indexOf(path) < 0) module.paths.push(path);
            }

            const sandbox = {
              require: require,
              console: console,
              process: process,
              Buffer: Buffer
            };
            sandbox.dataform = { projectConfig: payload.projectConfig || {} };

            function refName(args) {
              if (args.length === 0) return null;
              const first = args[0];
              if (first && typeof first === 'object') return first.name || null;
              if (args.length > 1) return String(args[args.length - 1]);
              return String(first);
            }

            function refImpl() {
              const name = refName(Array.prototype.slice.call(arguments));
              if (name === null) throw new Error('ref() requires a name');
              const target = (payload.refTargets || {})[name];
              if (!target) throw new Error('unknown ref: ' + name);
              return target;
            }

            sandbox.ref = refImpl;
            sandbox.resolve = refImpl;
            sandbox.self = function () {
              if (!payload.selfTarget) throw new Error('self() is not resolvable');
              return payload.selfTarget;
            };
            sandbox.incremental = function () { return false; };
            sandbox.when = function (condition, then, otherwise) {
              return condition ? then : (otherwise === undefined ? '' : otherwise);
            };

            const context = vm.createContext(sandbox);
            sandbox.ctx = sandbox;

            const includes = payload.includeSources || {};
            for (let pass = 0; pass < 2; pass++) {
              for (const name of Object.keys(includes)) {
                try {
                  const wrapped = '(function (module, exports, require) {\\n' + includes[name] + '\\n})';
                  const factory = vm.runInContext(wrapped, context, { filename: name + '.js', timeout: 2000 });
                  const included = { exports: {} };
                  factory(included, included.exports, require);
                  sandbox[name] = included.exports;
                } catch (e) {
                  if (pass === 1) sandbox[name] = sandbox[name] || {};
                }
              }
            }

            function stringify(value) {
              if (typeof value === 'string') return value;
              if (typeof value !== 'object') return String(value);
              try {
                return JSON.stringify(value, null, 2);
              } catch (e) {
                return String(value);
              }
            }

            const fileScript = payload.fileScript || '';

            function evaluate(source, withFileScript) {
              const body = withFileScript
                ? '(function () {\\n' + fileScript + '\\nreturn (' + source + ');\\n})()'
                : '(' + source + ')';
              return vm.runInContext(body, context, { timeout: 1000 });
            }

            const results = [];
            for (const source of payload.expressions || []) {
              let value;
              let error = null;
              try {
                value = evaluate(source, fileScript.length > 0);
              } catch (first) {
                try {
                  value = evaluate(source, false);
                } catch (second) {
                  error = (second && second.message) ? second.message : String(second);
                }
              }
              if (error !== null) {
                results.push({ source: source, error: error });
              } else if (typeof value === 'function') {
                results.push({ source: source, error: 'expression evaluated to a function' });
              } else if (value === undefined || value === null) {
                results.push({ source: source, error: 'expression evaluated to ' + String(value) });
              } else {
                results.push({ source: source, value: stringify(value) });
              }
            }

            process.stdout.write(JSON.stringify(results));
            """;

    private DataformEvalScriptBuilder() {
    }

    /**
     * Returns the harness source. It is constant, so the file written to disk can be reused.
     */
    @NotNull
    public static String script() {
        return SCRIPT;
    }

    /**
     * Serializes the payload the harness reads from standard input.
     */
    @NotNull
    public static String payload(@NotNull DataformEvaluationContext context, @NotNull List<String> expressions) {
        return GSON.toJson(new Payload(
                context.nodePaths(),
                context.projectConfig(),
                context.refTargets(),
                context.selfTarget(),
                context.includeSources(),
                context.fileScript(),
                expressions));
    }

    private record Payload(List<String> nodePaths,
                           Map<String, Object> projectConfig,
                           Map<String, String> refTargets,
                           String selfTarget,
                           Map<String, String> includeSources,
                           String fileScript,
                           List<String> expressions) {
    }
}
