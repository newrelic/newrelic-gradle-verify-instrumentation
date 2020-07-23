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
    File destinationDir = null;

    /**
     * Apply this plugin to the given target object.
     *
     * @param target The target object
     */
    @Override
    public void apply(Project target) {
        VerifyInstrumentationOptions verifyInstrumentation = target.getExtensions().create(VERIFIER_TASK_NAME, VerifyInstrumentationOptions.class);

        Task verifyCapstoneTask = target.task(VERIFIER_TASK_NAME);

        if (verifyInstrumentation.passesFileName == null || verifyInstrumentation.passesFileName.isEmpty()) {
            destinationDir = new File(target.getBuildDir(), "verifier");
            destinationDir.mkdir();
        }

        target.afterEvaluate(new AfterEvaluationAction(
                verifyInstrumentation,
                verifyCapstoneTask,
                target.getLogger(),
                destinationDir, new ProjectTaskFactory(target, target.getLogger(), destinationDir)));
    }

}
