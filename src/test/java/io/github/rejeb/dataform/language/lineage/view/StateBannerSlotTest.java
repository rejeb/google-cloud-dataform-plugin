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
package io.github.rejeb.dataform.language.lineage.view;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public class StateBannerSlotTest extends BasePlatformTestCase {

    public void testANewSlotIsHiddenWithoutBanner() {
        StateBannerSlot slot = new StateBannerSlot();

        assertFalse(slot.isVisible());
        assertNull(slot.banner());
        assertEquals(0, slot.getComponentCount());
    }

    public void testABannerIsShownAsTheOnlyChild() {
        StateBannerSlot slot = new StateBannerSlot();
        DataplexStateBanner banner = DataplexStateBanner.updateFailed("x", () -> {
        });

        slot.setBanner(banner);

        assertTrue(slot.isVisible());
        assertSame(banner, slot.banner());
        assertEquals(1, slot.getComponentCount());
        assertSame(banner.component(), slot.getComponent(0));
    }

    public void testNoBannerHidesTheSlot() {
        StateBannerSlot slot = new StateBannerSlot();
        slot.setBanner(DataplexStateBanner.updateFailed("x", () -> {
        }));

        slot.setBanner(null);

        assertFalse(slot.isVisible());
        assertNull(slot.banner());
        assertEquals(0, slot.getComponentCount());
    }

    public void testTheSameBannerTwiceKeepsItsComponent() {
        StateBannerSlot slot = new StateBannerSlot();
        DataplexStateBanner banner = DataplexStateBanner.updateFailed("x", () -> {
        });
        slot.setBanner(banner);
        java.awt.Component shown = slot.getComponent(0);

        slot.setBanner(banner);

        assertEquals(1, slot.getComponentCount());
        assertSame(shown, slot.getComponent(0));
    }
}
