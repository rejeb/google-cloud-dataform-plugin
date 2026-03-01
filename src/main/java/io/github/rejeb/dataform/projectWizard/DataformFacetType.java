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
package io.github.rejeb.dataform.projectWizard;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetType;
import com.intellij.facet.FacetTypeId;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DataformFacetType extends FacetType<DataformFacet, DataformFacetConfiguration> {
    public static final FacetTypeId<DataformFacet> ID = new FacetTypeId<>(DataformModuleType.ID);
    public static final DataformFacetType INSTANCE = new DataformFacetType();

    public DataformFacetType() {
        super(ID, DataformModuleType.ID, "Dataform Module");
    }

    @Override
    public DataformFacetConfiguration createDefaultConfiguration() {
        return new DataformFacetConfiguration();
    }

    @Override
    public DataformFacet createFacet(@NotNull Module module, String name,
                                     @NotNull DataformFacetConfiguration config,
                                     @Nullable Facet underlyingFacet) {
        return new DataformFacet(this, module, name, config);
    }

    @Override
    public boolean isSuitableModuleType(ModuleType moduleType) {
        return moduleType.getId().equals(DataformModuleType.ID);
    }
}