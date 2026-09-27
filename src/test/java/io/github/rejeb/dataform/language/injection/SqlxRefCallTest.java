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
package io.github.rejeb.dataform.language.injection;

import io.github.rejeb.dataform.language.compilation.model.ActionReference;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every form Dataform accepts for {@code ref()}: the qualified ones are what a project must write
 * as soon as two datasets hold a table of the same name.
 */
public class SqlxRefCallTest {

    private static ActionReference parse(String hole) {
        Optional<ActionReference> parsed = SqlxRefCall.parse(hole);
        assertTrue(parsed.isPresent(), "must parse " + hole);
        return parsed.get();
    }

    @Test
    public void aNameAlone() {
        assertEquals(new ActionReference(null, null, "customers"), parse("${ref(\"customers\")}"));
        assertEquals(new ActionReference(null, null, "customers"), parse("${ ref( 'customers' ) }"));
    }

    @Test
    public void aSchemaAndAName() {
        assertEquals(new ActionReference(null, "mart", "customers"),
                parse("${ref(\"mart\", \"customers\")}"));
    }

    @Test
    public void aDatabaseASchemaAndAName() {
        assertEquals(new ActionReference("p", "mart", "customers"),
                parse("${ref('p', 'mart', 'customers')}"));
    }

    @Test
    public void anObjectInAnyOrderWithQuotedOrBareKeys() {
        assertEquals(new ActionReference(null, "mart", "customers"),
                parse("${ref({ name: \"customers\", schema: \"mart\" })}"));
        assertEquals(new ActionReference("p", "mart", "customers"),
                parse("${ref({\"database\": 'p', 'schema': 'mart', name: 'customers'})}"));
    }

    @Test
    public void anArrayOfOneToThreeParts() {
        assertEquals(new ActionReference(null, null, "customers"), parse("${ref([\"customers\"])}"));
        assertEquals(new ActionReference(null, "mart", "customers"),
                parse("${ref([\"mart\", \"customers\"])}"));
        assertEquals(new ActionReference("p", "mart", "customers"),
                parse("${ref([ 'p', 'mart', 'customers' ])}"));
    }

    @Test
    public void anArrayIsTheOnlyArgumentAndHoldsAtMostThreeParts() {
        assertTrue(SqlxRefCall.parse("${ref([\"mart\"], \"customers\")}").isEmpty());
        assertTrue(SqlxRefCall.parse("${ref([])}").isEmpty());
        assertTrue(SqlxRefCall.parse("${ref(['a', 'b', 'c', 'd'])}").isEmpty());
    }

    @Test
    public void anObjectWithProjectAndDatasetKeys() {
        assertEquals(new ActionReference("p", "mart", "customers"),
                parse("${ref({ project: \"p\", dataset: \"mart\", name: \"customers\" })}"));
        assertEquals(new ActionReference(null, "mart", "customers"),
                parse("${ref({ dataset: \"mart\", name: \"customers\" })}"));
    }

    @Test
    public void projectAndDatasetKeysWinOverDatabaseAndSchema() {
        assertEquals(new ActionReference(null, "mart", "customers"),
                parse("${ref({ schema: \"other\", dataset: \"mart\", name: \"customers\" })}"));
    }

    @Test
    public void includeDependentAssertionsIsIgnored() {
        assertEquals(new ActionReference(null, "mart", "customers"),
                parse("${ref({ schema: \"mart\", name: \"customers\", includeDependentAssertions: true })}"));
    }

    @Test
    public void resolveTakesTheSameArguments() {
        assertEquals(new ActionReference(null, "mart", "customers"),
                parse("${resolve(\"mart\", \"customers\")}"));
        assertEquals(new ActionReference(null, null, "customers"), parse("${resolve('customers')}"));
    }

    @Test
    public void aDottedNameIsNotSplit() {
        assertEquals(new ActionReference(null, null, "mart.customers"), parse("${ref(\"mart.customers\")}"));
    }

    @Test
    public void anObjectWithoutANameIsNotAReference() {
        assertTrue(SqlxRefCall.parse("${ref({ schema: \"mart\" })}").isEmpty());
    }

    @Test
    public void anythingElseIsNotAReference() {
        assertTrue(SqlxRefCall.parse("${self()}").isEmpty());
        assertTrue(SqlxRefCall.parse("${ref(tableName)}").isEmpty());
        assertTrue(SqlxRefCall.parse("${ref('a', 'b', 'c', 'd')}").isEmpty());
    }
}
