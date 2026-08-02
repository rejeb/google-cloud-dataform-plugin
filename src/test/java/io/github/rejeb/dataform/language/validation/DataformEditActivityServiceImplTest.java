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
package io.github.rejeb.dataform.language.validation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DataformEditActivityServiceImplTest {

    private final DataformEditActivityServiceImpl service = new DataformEditActivityServiceImpl();

    @Test
    public void nothingHasBeenEditedYet() {
        assertFalse(service.isEditing());
        assertEquals(0, service.remainingQuietPeriodMs());
    }

    @Test
    public void anEditStartsTheQuietPeriod() {
        service.noteEdit();

        assertTrue(service.isEditing());
        assertTrue(service.remainingQuietPeriodMs() > 0);
    }

    @Test
    public void theQuietPeriodNeverExceedsItsLength() {
        service.noteEdit();

        assertTrue(service.remainingQuietPeriodMs()
                <= DataformEditActivityService.QUIET_PERIOD_MS);
    }

    @Test
    public void aLaterEditRestartsTheQuietPeriod() throws Exception {
        service.noteEdit();
        long afterFirst = service.remainingQuietPeriodMs();
        Thread.sleep(20);
        service.noteEdit();

        assertTrue(service.remainingQuietPeriodMs() >= afterFirst - 5,
                "each keystroke must push the wait back");
    }
}
