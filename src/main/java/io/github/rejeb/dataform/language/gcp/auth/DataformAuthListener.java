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
package io.github.rejeb.dataform.language.gcp.auth;

import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

/**
 * Hears the Google Cloud sign-in state change: signed in, signed out, sign-in started or failed.
 * Published on the EDT.
 */
public interface DataformAuthListener {

    @Topic.AppLevel
    Topic<DataformAuthListener> TOPIC = new Topic<>(DataformAuthListener.class, Topic.BroadcastDirection.NONE);

    /**
     * Called after the sign-in state changed to {@code status}.
     */
    void authStatusChanged(@NotNull AuthStatus status);
}
