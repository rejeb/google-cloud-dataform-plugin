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
package io.github.rejeb.dataform.language.ui.sqlviewer;

/**
 * How tall a {@link SqlViewer} is.
 */
public sealed interface SqlViewerHeight {

    /**
     * The viewer takes all the height its container gives it, as the only content of a tab.
     */
    record Fill() implements SqlViewerHeight {
    }

    /**
     * The viewer is as tall as its text, up to the given number of lines, then scrolls inside; used
     * where several viewers share a page.
     *
     * @param maxLines the number of lines shown before the text scrolls
     */
    record FitText(int maxLines) implements SqlViewerHeight {

        public FitText {
            if (maxLines < 1) throw new IllegalArgumentException("maxLines must be positive: " + maxLines);
        }
    }
}
