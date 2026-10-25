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
package io.github.rejeb.dataform.language.fileEditor;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;

import java.util.List;

class TableQuerySection extends CollapsibleSection {

    private final FormattedCompiledQuery query;
    private final QuerySection querySection;
    private final List<QuerySection> sections;

    TableQuerySection(FormattedCompiledQuery query, FileType fileType, Project project) {
        super(query.tableName(), verticalPanel());
        this.query = query;
        QuerySection preOps = new QuerySection("Pre Operations", fileType, project, false);
        QuerySection incrementalPreOps = new QuerySection("Incremental Pre Operations", fileType, project, false);
        querySection = new QuerySection("Query", fileType, project, false);
        QuerySection postOps = new QuerySection("Post Operations", fileType, project, false);
        QuerySection errors = new QuerySection("Compilation Errors", null, project, true);
        sections = List.of(preOps, incrementalPreOps, querySection, postOps, errors);
        sections.forEach(content()::add);

        preOps.setContent(query.preOps());
        incrementalPreOps.setContent(query.incrementalPreOps());
        querySection.setContent(query.query());
        postOps.setContent(query.postOps());
        errors.setContent(query.compilationErrors());
    }

    public QuerySection getQuerySection() {
        return querySection;
    }

    public FormattedCompiledQuery getQuery() {
        return this.query;
    }

    public void dispose() {
        sections.forEach(QuerySection::dispose);
    }
}
