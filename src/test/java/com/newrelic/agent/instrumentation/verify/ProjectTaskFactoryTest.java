/*
 * Copyright 2020 New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.instrumentation.verify;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.eclipse.aether.repository.RemoteRepository;
import org.gradle.api.GradleException;
import org.gradle.api.GradleScriptException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.UnknownTaskException;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.specs.Spec;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opentest4j.AssertionFailedError;
import org.slf4j.helpers.NOPLogger;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.newrelic.agent.instrumentation.verify.VerificationPlugin.VERIFIER_TASK_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class ProjectTaskFactoryTest {
    @Test
    void shouldFailWhenPassesOnlyYieldsEmpty() {
        givenMavenClientReturnsNoResults();
        givenProjectIsConfigured();
        givenVersionsOneToThreeArePassesOnly();
        givenTaskFactoryWithNoExcludes();
        thenPassesOnlyTasksFailToBuild(GradleException.class);
    }

    @Test
    void shouldSucceedWhenPassesOnlyYieldsEmpty() {
        givenMavenClientReturnsVersionTwo();
        givenProjectIsConfigured();
        givenVersionsOneToThreeArePassesOnly();
        givenTaskFactoryWithNoExcludes();
        whenPassesOnlyTasksAreBuilt();
        thenASingleVersionTwoTaskResults();
    }

    @Test
    void shouldLogFailuresAndSuccessesToDefaults() {
        givenMavenClientReturnsVersionsOutsidePassRange();
        givenProjectIsConfigured();
        givenTaskFactoryWithNoExcludes();
        givenVersionsOneToThreeArePassesOnly();
        whenPassesOnlyTasksAreBuilt();
        thenTasksLogToDefaultFiles();
    }

    @Test
    void shouldLogFailureToDefaultAndSuccessesToNamedFile() {
        givenMavenClientReturnsVersionsOutsidePassRange();
        givenProjectIsConfigured();
        givenVersionsOneToThreeArePassesOnly();
        givenThePassesFileOptionIsSet();
        givenTaskFactoryWithNoExcludes();
        whenPassesOnlyTasksAreBuilt();
        thenFailsLogToDefaultAndSuccessesToNamedFile();
    }

    @Test
    void shouldFailWhenOnlyAvailableVersionIsExcluded() {
        givenMavenClientReturnsVersionTwo();
        givenProjectIsConfigured();
        givenVersionsOneToThreeArePassesOnly();
        givenTaskFactoryExcludingVersionTwo();
        thenPassesOnlyTasksFailToBuild(GradleException.class);
    }

    @Test
    void shouldFailWhenOnlyAvailableVersionIsExplicitFail() {
        givenMavenClientReturnsVersionTwo();
        givenProjectIsConfigured();
        givenVersionsOneToThreeArePassesOnlyAndTwoIsFails();
        givenTaskFactoryWithNoExcludes();
        thenPassesOnlyTasksFailToBuild(GradleException.class);
    }

    @Test
    void shouldProduceFailsOnEitherSideOfPassesOnly() {
        givenMavenClientReturnsVersionsOutsidePassRange();
        givenProjectIsConfigured();
        givenVersionsOneToThreeArePassesOnly();
        givenTaskFactoryWithNoExcludes();
        whenPassesOnlyTasksAreBuilt();
        thenTwoFailTasksAndOnePassTaskResults();
    }

    @Test
    void shouldHonorExplicitFailsFirst() {
        givenMavenClientReturnsVersionsInsideAndOutsidePassRange();
        givenProjectIsConfigured();
        givenVersionsOneToThreeArePassesOnlyAndTwoPointOneIsFails();
        givenTaskFactoryWithNoExcludes();
        whenPassesOnlyTasksAreBuilt();
        thenThreeFailTasksAndOnePassTaskResults();
    }

    @Test
    void shouldGeneratePassesAndFailsPerNormal() {
        givenMavenClientReturnsVersionsInsideAndOutsidePassRange();
        givenProjectIsConfigured();
        givenVersionsOneToThreeArePassesAndPastThreeAreFails();
        givenTaskFactoryWithNoExcludes();
        whenPassFailTasksAreBuilt();
        thenTwoPassTasksAndOneFailTaskResults();
    }

    @Test
    void shouldGenerateClasspathTaskCorrectly() {
        givenMavenClientReturnsVersionTwo();
        givenProjectIsConfigured();
        givenVersionsOneToThreeArePassesOnly();
        givenTaskFactoryWithNoExcludes();
        whenClasspathTaskIsBuilt();
        thenClasspathTaskIsCorrect();
    }

    @Test
    void shouldFailWithNoAgent() {
        givenMavenClientReturnsVersionTwo();
        givenProjectIsConfigured(ProjectTweaks.WITHOUT_AGENT);
        givenVersionsOneToThreeArePassesOnly();
        givenTaskFactoryWithNoExcludes();
        thenPassesOnlyTasksFailToBuild(GradleScriptException.class);
    }

    @Test
    void shouldFailWithNoJarTask() {
        givenMavenClientReturnsVersionTwo();
        givenProjectIsConfigured(ProjectTweaks.WITHOUT_JAVA_PLUGIN);
        givenTaskFactoryWithNoExcludes();
        givenVersionsOneToThreeArePassesOnly();
        thenPassesOnlyTasksFailToBuild(UnknownTaskException.class);
    }

    private void givenMavenClientReturnsNoResults() {
        MavenClient.INSTANCE = new MavenClient() {
            @Override
            public Collection<String> resolveAvailableVersions(String rangeDep, List<RemoteRepository> repositories) {
                return Collections.emptyList();
            }
        };
    }

    private void givenMavenClientReturnsVersionsOutsidePassRange() {
        MavenClient.INSTANCE = new MavenClient() {
            @Override
            public Collection<String> resolveAvailableVersions(String rangeDep, List<RemoteRepository> repositories) {
                return (rangeDep.contains(":[0,)")) // implicit fails
                        ? Arrays.asList("foo:bar:0.5", "foo:bar:2.0", "foo:bar:3.3")
                        : Collections.singletonList("foo:bar:2.0"); // passesOnly
            }
        };
    }

    private void givenMavenClientReturnsVersionsInsideAndOutsidePassRange() {
        MavenClient.INSTANCE = new MavenClient() {
            @Override
            public Collection<String> resolveAvailableVersions(String rangeDep, List<RemoteRepository> repositories) {
                if (rangeDep.contains(":[0,)")) // implicit fails
                    return Arrays.asList("foo:bar:0.5", "foo:bar:2.0", "foo:bar:2.1", "foo:bar:3.3");
                else if (rangeDep.contains(":[1.0,3.0)")) // passesOnly
                    return Arrays.asList("foo:bar:2.0", "foo:bar:2.1");
                else if (rangeDep.contains(":[3.0,)")) // passesOnly
                    return Collections.singletonList("foo:bar:3.3");
                else if (rangeDep.contains(":2.1")) // explicit fails
                    return Collections.singletonList("foo:bar:2.1");
                else if (rangeDep.matches(":\\d\\.\\d$"))
                    return Collections.singletonList(rangeDep);

                throw new AssertionFailedError("Some other value was requested: " + rangeDep);
            }
        };
    }

    private void givenMavenClientReturnsVersionTwo() {
        MavenClient.INSTANCE = new MavenClient() {
            @Override
            public Collection<String> resolveAvailableVersions(String rangeDep, List<RemoteRepository> repositories) {
                return Collections.singletonList("foo:bar:2.0");
            }
        };
    }

    private void givenProjectIsConfigured(ProjectTweaks... tweaks) {
        project = ProjectBuilder.builder().withName("myproject").build();
        List<ProjectTweaks> tweakList = Arrays.asList(tweaks);
        if (!tweakList.contains(ProjectTweaks.WITHOUT_JAVA_PLUGIN)) {
            project.getPluginManager().apply("java");
            project.getDependencies().add("implementation", "foo:bar:2.3");
            project.getDependencies().add("implementation", "foo:baz:10.4.5");
        }

        project.getConfigurations().create(VERIFIER_TASK_NAME);

        if (!tweakList.contains(ProjectTweaks.WITHOUT_AGENT)) {
            project.getDependencies()
                    .add(VERIFIER_TASK_NAME, project.files("/not/real/newrelic-agent.jar"));
        }

         verifyOptions = new VerifyInstrumentationOptions();
    }

    private void givenTaskFactoryExcludingVersionTwo() {
        target = new ProjectTaskFactory(project, Collections.singletonList("foo:bar:2.0"), NOPLogger.NOP_LOGGER, tempDir.toFile());
        target.setPassesFile(verifyOptions.passesFileName);
    }

    private void givenTaskFactoryWithNoExcludes() {
        target = new ProjectTaskFactory(project, Collections.emptySet(), NOPLogger.NOP_LOGGER, tempDir.toFile());
        target.setPassesFile(verifyOptions.passesFileName);
    }

    private void givenThePassesFileOptionIsSet() {
        verifyOptions.passesFileName = "/tmp/garbage/foo/don-t-worry.txt";
    }

    private void givenVersionsOneToThreeArePassesOnly() {
        verifyOptions.passesOnly("foo:bar:[1.0,3.0)");
    }

    private void givenVersionsOneToThreeArePassesOnlyAndTwoPointOneIsFails() {
        verifyOptions.passesOnly("foo:bar:[1.0,3.0)");
        verifyOptions.fails("foo:bar:2.1");
    }

    private void givenVersionsOneToThreeArePassesOnlyAndTwoIsFails() {
        verifyOptions.passesOnly("foo:bar:[1.0,3.0)");
        verifyOptions.fails("foo:bar:2.0");
    }

    private void givenVersionsOneToThreeArePassesAndPastThreeAreFails() {
        verifyOptions.passes("foo:bar:[1.0,3.0)");
        verifyOptions.fails("foo:bar:[3.0,)");
    }

    private void thenTasksLogToDefaultFiles() {
        AtomicBoolean foundAny = new AtomicBoolean(false);
        resultTasks
                .map(task -> (VerifyTask) task)
                .peek(task -> foundAny.set(true))
                .forEach(task -> {
                    foundAny.set(true);
                    assertEquals(tempDir + "/failures.txt", task.getParameters().getVerifierFailuresFile().getAbsolutePath());
                    assertEquals(tempDir + "/passes.txt", task.getParameters().getVerifierPassesFile().getAbsolutePath());
                });
        assertTrue(foundAny.get(), "Didn't find any tasks to verify :-(");
    }

    private void thenFailsLogToDefaultAndSuccessesToNamedFile() {
        AtomicBoolean foundAny = new AtomicBoolean(false);
        resultTasks
                .map(task -> (VerifyTask) task)
                .peek(task -> foundAny.set(true))
                .forEach(task -> {
                    foundAny.set(true);
                    assertEquals(tempDir + "/failures.txt", task.getParameters().getVerifierFailuresFile().getAbsolutePath());
                    assertEquals("/tmp/garbage/foo/don-t-worry.txt", task.getParameters().getVerifierPassesFile().getAbsolutePath());
                });
        assertTrue(foundAny.get(), "Didn't find any tasks to verify :-(");
    }

    private void thenClasspathTaskIsCorrect() {
        Optional<? extends Task> maybeFirstTask = resultTasks.findFirst();
        assertTrue(maybeFirstTask.isPresent());
        VerifyTask classpathTask = (VerifyTask) maybeFirstTask.get();

        assertTrue(classpathTask.getParameters().shouldSuccessfullyApply());

        DependencySet dependencies = project
                .getConfigurations()
                .getByName("config_classpath")
                .getDependencies();

        assertEquals(dependencies.stream()
                        .map(dependency -> dependency.getGroup() + ":" + dependency.getName() + ":" + dependency.getVersion())
                        .collect(Collectors.toSet()),
                ImmutableSet.of("foo:bar:2.3", "foo:baz:10.4.5")
        );
    }

    private void whenPassFailTasksAreBuilt() {
        resultTasks = target.buildExplicitPassFailTasks(verifyOptions);
    }

    private void whenPassesOnlyTasksAreBuilt() {
        resultTasks = target.buildTasksForPassesOnly(verifyOptions);
    }

    private void whenClasspathTaskIsBuilt() {
        resultTasks = target.buildClasspathTasks();
    }

    private void thenASingleVersionTwoTaskResults() {
        resultTasksMatch(
                ImmutableMap.of("2.0", true)
        );
    }

    private void thenTwoFailTasksAndOnePassTaskResults() {
        resultTasksMatch(
                ImmutableMap.of("2.0", true, "0.5", false, "3.3", false)
        );
    }

    private void thenTwoPassTasksAndOneFailTaskResults() {
        resultTasksMatch(
                ImmutableMap.of("2.0", true, "2.1", true, "3.3", false)
        );
    }

    private void thenThreeFailTasksAndOnePassTaskResults() {
        resultTasksMatch(
                ImmutableMap.of("2.0", true, "0.5", false, "2.1", false, "3.3", false)
        );
    }

    private void resultTasksMatch(Map<String, Boolean> matchers) {
        List<VerifyTask> actualTasks = resultTasks.map(task -> (VerifyTask) task).collect(Collectors.toList());
        assertEquals(matchers.size(), actualTasks.size());

        for (Map.Entry<String, Boolean> matcher : matchers.entrySet()) {
            boolean found = false;
            for (VerifyTask task : actualTasks) {
                if (taskConfigHasVersion(task, matcher.getKey()) && task.getParameters().shouldSuccessfullyApply() == matcher.getValue()) {
                    found = true;
                    actualTasks.remove(task);
                    break;
                }
            }
            if (!found) {
                fail("Could not find a task for version " + matcher.getKey() + " where assertPass is " + matcher.getValue());
            }
        }

        assertEquals(0, actualTasks.size(), "There were tasks left at the end that didn't match any matcher!");
    }

    private boolean taskConfigHasVersion(VerifyTask task, String version) {
        String configName = task.getName().replaceFirst("verify[A-Z][a-z]*", "config");

        DependencySet dependencies = project
                .getConfigurations()
                .getByName(configName)
                .getDependencies();

        return dependencies.matching(hasVersion(null)).size() == 1
                && dependencies.matching(hasVersion(version)).size() == 1;
    }

    private Spec<? super Dependency> hasVersion(String version) {
        return element ->
                "foo".equals(element.getGroup())
                        && "bar".equals(element.getName())
                        && (version == null || Objects.equals(element.getVersion(), version));
    }

    private <T extends Throwable> void thenPassesOnlyTasksFailToBuild(Class<T> exceptionClass) {
        assertThrows(exceptionClass, () -> {
            Set<? extends Task> actualTasks = target.buildTasksForPassesOnly(verifyOptions).collect(Collectors.toSet());
            // for easy breakpoint; we should never get here.
            assertEquals(0, actualTasks.size());
        });
    }

    @BeforeEach
    void classSetUp() {
        savedClient = MavenClient.INSTANCE;
    }

    @AfterEach
    void classTearDown() {
        MavenClient.INSTANCE = savedClient;
    }

    @SuppressWarnings("WeakerAccess") // @TempDir will fail if this is private.
    @TempDir
    Path tempDir;

    private Stream<? extends Task> resultTasks;
    private ProjectTaskFactory target;
    private VerifyInstrumentationOptions verifyOptions;
    private Project project;
    private MavenClient savedClient;

    enum ProjectTweaks {
        WITHOUT_AGENT,
        WITHOUT_JAVA_PLUGIN
    }
}