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
package io.github.rejeb.dataform.language.gcp.execution.workflow.repository;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class JobReservationReaderTest {

    @Test
    void theReservationIsNamedByItsShortName() {
        assertEquals("prod-etl",
                JobReservationReader.parse("{\"statistics\":{\"reservation_id\":\"admin-project:EU.prod-etl\"}}"));
        assertEquals("prod-etl", JobReservationReader.parse(
                "{\"statistics\":{\"reservation_id\":\"projects/admin/locations/EU/reservations/prod-etl\"}}"));
    }

    @Test
    void anOnDemandJobHasNoReservation() {
        assertNull(JobReservationReader.parse("{\"statistics\":{}}"));
        assertNull(JobReservationReader.parse("{}"));
        assertNull(JobReservationReader.parse("{\"statistics\":{\"reservation_id\":\"\"}}"));
    }

    @Test
    void aJobWithoutLocationIsReadWithoutFailing() {
        assertNull(JobReservationReader.read("p", null, "job"));
    }

    @Test
    void anUnreadableAnswerHasNoReservation() {
        assertNull(JobReservationReader.parse("not json"));
    }
}
