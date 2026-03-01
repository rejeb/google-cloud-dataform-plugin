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
package io.github.rejeb.dataform.language.schema;

import java.util.ArrayList;
import java.util.List;

public final class ProtoModel {

    private ProtoModel() {}

    public static final class ProtoFile {
        public final List<ProtoMessage> messages = new ArrayList<>();
        public final List<ProtoEnum>    enums    = new ArrayList<>();
    }

    public static final class ProtoMessage {
        public final String name;
        public final String qualifiedName; // e.g. "ActionConfig.TableConfig"
        public final String description;
        public final List<ProtoField>   fields          = new ArrayList<>();
        public final List<ProtoMessage> nestedMessages  = new ArrayList<>();
        public final List<ProtoEnum>    nestedEnums     = new ArrayList<>();

        public ProtoMessage(String name, String qualifiedName, String description) {
            this.name          = name;
            this.qualifiedName = qualifiedName;
            this.description   = description;
        }
    }

    public static final class ProtoField {
        public final String  name;
        public final String  camelName;
        public final String  type;
        public final boolean repeated;
        public final boolean isMap;
        public final String  mapKeyType;
        public final String  mapValueType;
        public final String  description;
        public final boolean isOneof;

        public ProtoField(String name, String camelName, String type,
                          boolean repeated, boolean isMap,
                          String mapKeyType, String mapValueType,
                          String description, boolean isOneof) {
            this.name         = name;
            this.camelName    = camelName;
            this.type         = type;
            this.repeated     = repeated;
            this.isMap        = isMap;
            this.mapKeyType   = mapKeyType;
            this.mapValueType = mapValueType;
            this.description  = description;
            this.isOneof      = isOneof;
        }
    }

    public static final class ProtoEnum {
        public final String       name;
        public final String       qualifiedName;
        public final List<String> values = new ArrayList<>();

        public ProtoEnum(String name, String qualifiedName) {
            this.name          = name;
            this.qualifiedName = qualifiedName;
        }
    }
}
