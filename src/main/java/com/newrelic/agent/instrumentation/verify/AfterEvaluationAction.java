/*
 * Copyright 2020 New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.instrumentation.verify;

import com.google.common.annotations.VisibleForTesting;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.eclipse.aether.repository.RemoteRepository;
import org.gradle.api.*;
import org.slf4j.Logger;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.newrelic.agent.instrumentation.verify.VerificationPlugin.VERIFIER_TASK_NAME;

public class AfterEvaluationAction implements Action<Project> {
    private VerifyInstrumentationOptions verifyOptions;
    private Task verifyInstrumentationTask;
    private final Logger logger;
    private final File passesFileDir;
    //this is for testing
    private final Function<Project, List<RemoteRepository>> getRepositoriesFunction;


    AfterEvaluationAction(VerifyInstrumentationOptions verifyOptions, Task verifyInstrumentationTask, Logger logger, File passesFileDir) {
        this(verifyOptions, verifyInstrumentationTask, logger, passesFileDir, MavenProjectUtil::getMavenRepositories);
    }

    //this is for testing
    AfterEvaluationAction(VerifyInstrumentationOptions verifyOptions, Task verifyInstrumentationTask, Logger logger, File passesFileDir, Function<Project, List<RemoteRepository>> getRepositoriesFunction) {
        this.verifyOptions = verifyOptions;
        this.verifyInstrumentationTask = verifyInstrumentationTask;
        this.logger = logger;
        this.passesFileDir = passesFileDir;
        this.getRepositoriesFunction = getRepositoriesFunction;
    }

    /**
     * Performs this action against the given object.
     *
     * @param project The object to perform the action on.
     */
    @Override
    public void execute(@NonNull Project project) {
        if (!projectRequiresVerification(project)) {
            // only prepare dependencies if verifyInstrumentation task is requested
            return;
        }

        if (verifyOptions.passesOnly().size() + verifyOptions.passes().size() == 0) {
            logger.info("Nothing to do - 'passesOnly' or 'passes' is required.");
            return;
        }

        if (verifyOptions.passesOnly().size() > 0 == verifyOptions.passes().size() > 0) {
            throw new GradleException("'passesOnly' cannot be specified with 'passes'.");
        }

        // get the repository sources from the user's build.gradle

        List<RemoteRepository> mavenRepositories = getRepositoriesFunction.apply(project);

        // create collection of excludes
        Set<String> excludedVersions = buildExcludedVersions(verifyOptions, mavenRepositories, MavenClient.INSTANCE);

        ProjectTaskFactory taskFactory = new ProjectTaskFactory(project, excludedVersions, logger, passesFileDir);
        taskFactory.setPassesFile(verifyOptions.passesFileName);

        // Configuration to download/reference the agent.
        createProjectDependencyOnAgent(project, verifyOptions.getNrAgent());

        Stream<? extends Task> classPathTasks = verifyOptions.verifyClasspath
                ? taskFactory.buildClasspathTasks()
                : Stream.empty();

        // create verification task for version ranges
        Stream<Task> passFailTasks = (verifyOptions.passesOnly().size() > 0)
                ? taskFactory.buildTasksForPassesOnly(verifyOptions)
                : taskFactory.buildExplicitPassFailTasks(verifyOptions);

        verifyInstrumentationTask.dependsOn(project.getTasks().getByName("jar"));

        Stream.concat(classPathTasks, passFailTasks)
                .forEach(verifyInstrumentationTask::finalizedBy);
    }

    /**
     * True if we're going to execute verifyInstrumentation on the given project or subproject.
     *
     * Useful in the evaluate phase to add dependencies before the execution phase for only the projects we will actually verify.
     */
    public boolean projectRequiresVerification(Project project) {
        return project.getGradle().getStartParameter().getTaskNames().stream()
                .filter(taskName -> taskName.endsWith(VERIFIER_TASK_NAME))
                .map(taskName -> getProjectPath(project, taskName))
                .filter(Objects::nonNull)
                .anyMatch(projectName -> project.getProjectDir().getPath().startsWith(projectName));
    }

    private String getProjectPath(Project project, String taskName) {
        String projectWithVerifyDir = taskName.replaceFirst(":?" + VERIFIER_TASK_NAME + "$", "").replaceFirst("^:*", ":");
        if (projectWithVerifyDir.equals(":")) {
            return project.getGradle().getStartParameter().getCurrentDir().getPath();
        }
        try {
            return project.project(projectWithVerifyDir).getProjectDir().getPath();
        } catch (UnknownProjectException ignored) {
            return null;
        }
    }

    @VisibleForTesting
    public void createProjectDependencyOnAgent(Project project, Object nrAgent) {
        project.getConfigurations().create(VERIFIER_TASK_NAME);
        if (nrAgent instanceof File) {
            // allow a local file defined agent
            project.getDependencies().add(VERIFIER_TASK_NAME, project.files(nrAgent));
        } else {
            // or as a remote dependency
            project.getDependencies().add(VERIFIER_TASK_NAME, nrAgent);
        }
    }

    @VisibleForTesting
    public Set<String> buildExcludedVersions(VerifyInstrumentationOptions verifyOptions, List<RemoteRepository> mavenRepositories, MavenClient mavenClient) {
        Set<String> excludedVersions = new HashSet<>(verifyOptions.excludeRegex());

        Set<String> resolvedExclusions = verifyOptions.exclude().stream()
                .flatMap((String excludeRange) ->
                        mavenClient.resolveAvailableVersions(excludeRange, mavenRepositories).stream()
                                .peek(dep -> logger.info("Excluding artifact: " + dep)))
                .collect(Collectors.toSet());

        excludedVersions.addAll(resolvedExclusions);

        return excludedVersions;
    }
}
