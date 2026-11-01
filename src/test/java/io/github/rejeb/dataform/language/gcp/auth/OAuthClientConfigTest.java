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

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class OAuthClientConfigTest {

    @Test
    public void decodesBase64Value() {
        String encoded = Base64.getEncoder()
                .encodeToString("123-abc.apps.googleusercontent.com".getBytes(StandardCharsets.UTF_8));

        assertEquals("123-abc.apps.googleusercontent.com", OAuthClientConfig.decode(encoded));
    }

    @Test
    public void ignoresSurroundingWhitespace() {
        String encoded = Base64.getEncoder().encodeToString("GOCSPX-secret".getBytes(StandardCharsets.UTF_8));

        assertEquals("GOCSPX-secret", OAuthClientConfig.decode("  " + encoded + "\n"));
    }

    @Test
    public void emptyValueDecodesToEmpty() {
        assertEquals("", OAuthClientConfig.decode(""));
    }

    @Test
    public void invalidBase64DecodesToEmpty() {
        assertEquals("", OAuthClientConfig.decode("not base64 !"));
    }
}
