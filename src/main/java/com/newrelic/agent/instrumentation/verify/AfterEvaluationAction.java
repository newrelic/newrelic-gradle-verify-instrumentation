/*
 * Copyright 2020 New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.instrumentation.verify;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.eclipse.aether.repository.RemoteRepository;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.UnknownProjectException;
import org.slf4j.Logger;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.newrelic.agent.instrumentation.verify.VerificationPlugin.VERIFIER_TASK_NAME;

public class AfterEvaluationAction implements Action<Project> {
    private VerifyInstrumentationOptions options;
    private Task verifyCapstoneTask;
    private final Logger logger;
    private final File destinationDir;

    AfterEvaluationAction(VerifyInstrumentationOptions options, Task verifyCapstoneTask, Logger logger, File destinationDir) {
        this.options = options;
        this.verifyCapstoneTask = verifyCapstoneTask;
        this.logger = logger;
        this.destinationDir = destinationDir;
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

        if(!options.verifyClasspath) {
            if (options.passesOnly().size() + options.passes().size() == 0) {
                logger.info("Nothing to do - 'passesOnly' or 'passes' or jar on classpath is required.");
                return;
            }

            if (options.passesOnly().size() > 0 == options.passes().size() > 0) {
                throw new GradleException("'passesOnly' cannot be specified with 'passes'.");
            }
        }

        // get the repository sources from the user's build.gradle
        List<RemoteRepository> mavenRepositories = MavenProjectUtil.getMavenRepositories(project);

        // create collection of excludes
        Set<String> excludedVersions = buildExcludedVersions(options, mavenRepositories);

        ProjectTaskFactory taskFactory = new ProjectTaskFactory(project, excludedVersions, logger, destinationDir);
        taskFactory.setPassesFile(options.passesFileName);

        // Configuration to download/reference the agent.
        createProjectDependencyOnAgent(project, options.getNrAgent());

        Stream<? extends Task> classPathTasks = options.verifyClasspath
                ? taskFactory.buildClasspathTasks()
                : Stream.empty();

        // create verification task for version ranges
        Stream<Task> passFailTasks = (options.passesOnly().size() > 0)
                ? taskFactory.buildTasksForPassesOnly(options)
                : taskFactory.buildExplicitPassFailTasks(options);

        verifyCapstoneTask.dependsOn(project.getTasks().getByName("jar"));

        Stream.concat(classPathTasks, passFailTasks)
                .forEach(verifyCapstoneTask::finalizedBy);
    }

    /**
     * True if we're going to execute verifyInstrumentation on the given project or subproject.
     *
     * Useful in the evaluate phase to add dependencies before the execution phase for only the projects we will actually verify.
     */
    static boolean projectRequiresVerification(Project project) {
        for (String taskName : project.getGradle().getStartParameter().getTaskNames()) {
            if (!taskName.endsWith(VERIFIER_TASK_NAME)) {
                continue;
            }

            String projectWithVerifyDir = taskName.replaceFirst(":?" + VERIFIER_TASK_NAME + "$", "").replaceFirst("^:*", ":");
            if (projectWithVerifyDir.equals(":")) {
                projectWithVerifyDir = project.getGradle().getStartParameter().getCurrentDir().getPath();
            } else {
                try {
                    projectWithVerifyDir = project.project(projectWithVerifyDir).getProjectDir().getPath();
                } catch (UnknownProjectException ignored) {
                    continue;
                }
            }
            // only prepare dependencies for the project(s) requested
            if (project.getProjectDir().getPath().startsWith(projectWithVerifyDir)) {
                return true;
            }
        }
        return false;
    }

    private void createProjectDependencyOnAgent(Project project, Object nrAgent) {
        project.getConfigurations().create(VERIFIER_TASK_NAME);
        if (nrAgent instanceof File) {
            // allow a local file defined agent
            project.getDependencies().add(VERIFIER_TASK_NAME, project.files(nrAgent));
        } else {
            // or as a remote dependency
            project.getDependencies().add(VERIFIER_TASK_NAME, nrAgent);
        }
    }

    private Set<String> buildExcludedVersions(VerifyInstrumentationOptions verifyInstrumentation, List<RemoteRepository> mavenRepositories) {
        Set<String> excludedVersions = new HashSet<>(verifyInstrumentation.excludeRegex());

        Set<String> resolvedExclusions = verifyInstrumentation.exclude().stream()
                .flatMap((String excludeRange) ->
                        MavenClient.INSTANCE.resolveAvailableVersions(excludeRange, mavenRepositories).stream()
                                .peek(dep -> logger.info("Excluding artifact: " + dep)))
                .collect(Collectors.toSet());

        excludedVersions.addAll(resolvedExclusions);

        return excludedVersions;
    }
}
