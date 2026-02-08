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
package io.github.rejeb.dataform.language.startup;

import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DataformProjectStartup implements ProjectActivity {

    @Nullable
    @Override
    public Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
//        disableSqlResolveInspection(project);
        DumbService.getInstance(project).runWhenSmart(() -> {
        });
        return null;
    }

//    private static void disableSqlResolveInspection(Project project) {
//        InspectionProfileManager profileManager = InspectionProfileManager.getInstance(project);
//        InspectionProfileImpl profile = profileManager.getCurrentProfile();
//
//        // Désactive l'inspection SqlResolve
//        profile.setToolEnabled("SqlResolve", false);
//        profile.setToolEnabled("SqlUnresolvedReference", false);
//
//        // Si vous voulez désactiver toutes les inspections SQL
//        profile.setToolEnabled("SqlNoDataSourceInspection", false);
//    }
}