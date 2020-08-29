/*
 * Copyright 2020 New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.instrumentation.verify;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.tasks.OutputDirectory;

import java.io.File;

public class VerificationPlugin implements Plugin<Project> {

    static final String VERIFIER_TASK_NAME = "verifyInstrumentation";

    @OutputDirectory
    File passesFileDir = null;

    /**
     * Apply this plugin to the given target object.
     *
     * @param target The target object
     */
    @Override
    public void apply(Project target) {
        VerifyInstrumentationOptions verifyOptions = target.getExtensions().create(VERIFIER_TASK_NAME, VerifyInstrumentationOptions.class);

        Task verifyInstrumentationTask = target.task(VERIFIER_TASK_NAME);

        if (verifyOptions.passesFileName == null || verifyOptions.passesFileName.isEmpty()) {
            passesFileDir = new File(target.getBuildDir(), "verifier");
            passesFileDir.mkdir();
        }

        target.afterEvaluate(new AfterEvaluationAction(
                verifyOptions,
                verifyInstrumentationTask,
                target.getLogger(),
                passesFileDir));
    }

}
