/*
 * Copyright 2020 New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.instrumentation.verify;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.gradle.workers.WorkerExecutor;

import javax.inject.Inject;

@SuppressWarnings("UnstableApiUsage")
public class VerifyTask extends DefaultTask {
    private final WorkerExecutor workerExecutor;

    private VerifyParameters parameters;

    @Inject
    public VerifyTask(WorkerExecutor workerExecutor) {
        super();
        this.workerExecutor = workerExecutor;
    }

    @TaskAction
    public void verify() {
        workerExecutor.noIsolation().submit(
                VerifyWorkAction.class,
                // IDEA might not like this. However, it does work and compile.
                parameters -> parameters.setFrom(this.parameters).setTaskName(getName()));
    }

    public void setParameters(VerifyParameters parameters) {
        this.parameters = parameters;
    }

    @Input
    public VerifyParameters getParameters() {
        return parameters;
    }

}