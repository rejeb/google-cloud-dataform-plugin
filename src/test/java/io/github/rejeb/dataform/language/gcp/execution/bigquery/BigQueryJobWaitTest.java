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
package io.github.rejeb.dataform.language.gcp.execution.bigquery;

import com.google.cloud.bigquery.Job;
import com.google.cloud.bigquery.JobStatus;
import com.intellij.openapi.progress.ProgressIndicator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Waiting for a query job returns a snapshot of the finished job, whose statistics carry the end
 * time and the bytes, and never a snapshot taken while it was still running.
 */
public class BigQueryJobWaitTest {

    @Test
    public void returnsTheReloadedJobWhenTheJobEndsBetweenAReloadAndTheStatusCheck() throws Exception {
        Job running = job(JobStatus.State.RUNNING);
        Job done = job(JobStatus.State.DONE);
        when(running.isDone()).thenReturn(true);
        when(running.reload()).thenReturn(done);

        Job result = BigQueryExecutionServiceImpl.waitFor(running, mock(ProgressIndicator.class));

        assertSame(done, result);
    }

    @Test
    public void keepsASubmittedJobThatIsAlreadyDone() throws Exception {
        Job done = job(JobStatus.State.DONE);

        Job result = BigQueryExecutionServiceImpl.waitFor(done, mock(ProgressIndicator.class));

        assertSame(done, result);
        verify(done, never()).reload();
    }

    private static Job job(JobStatus.State state) {
        JobStatus status = mock(JobStatus.class);
        when(status.getState()).thenReturn(state);
        Job job = mock(Job.class);
        when(job.getStatus()).thenReturn(status);
        return job;
    }
}
