/*
 * Copyright 2020 New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.instrumentation.verify

import org.eclipse.aether.repository.RemoteRepository
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.invocation.Gradle
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test

import java.util.stream.Collectors

import static org.junit.jupiter.api.Assertions.*

class VerifierTest {
    @Test
    void testVerifierTaskAdded() {
        Project project = ProjectBuilder.builder().build()
        project.tasks.create("jar")
        project.pluginManager.apply 'com.newrelic.gradle-verify-instrumentation-plugin'

        assertNotNull(project.tasks.verifyInstrumentation)
    }

    @Test
    void testAddDependenciesForVerifierTasks() {
        Project root = ProjectBuilder.builder().withName("root").build()
        Project sub1 = ProjectBuilder.builder().withName("sub1").withParent(root).build()
        Project subsub1 = ProjectBuilder.builder().withName("subsub1").withParent(sub1).build()
        Project sub2 = ProjectBuilder.builder().withName("sub2").withParent(root).build()
        Gradle gradle = root.gradle

        // simulate: gradle jar verifyInstrumentation
        gradle.startParameter.setTaskNames(["jar", "verifyInstrumentation"])
        gradle.startParameter.setCurrentDir(root.getProjectDir())
        assertTrue(AfterEvaluationAction.projectRequiresVerification(root))
        assertTrue(AfterEvaluationAction.projectRequiresVerification(sub1))
        assertTrue(AfterEvaluationAction.projectRequiresVerification(subsub1))
        assertTrue(AfterEvaluationAction.projectRequiresVerification(sub2))

        // simulate: gradle jar sub1:verifyInstrumentation publish
        gradle.startParameter.setTaskNames(["jar", "sub1:verifyInstrumentation", "publish"])
        gradle.startParameter.setCurrentDir(root.getProjectDir())
        assertFalse(AfterEvaluationAction.projectRequiresVerification(root))
        assertTrue(AfterEvaluationAction.projectRequiresVerification(sub1))
        assertTrue(AfterEvaluationAction.projectRequiresVerification(subsub1))
        assertFalse(AfterEvaluationAction.projectRequiresVerification(sub2))

        // simulate: cd sub1 && gradle verifyInstrumentation
        gradle.startParameter.setTaskNames(["verifyInstrumentation"])
        gradle.startParameter.setCurrentDir(sub1.getProjectDir())
        assertFalse(AfterEvaluationAction.projectRequiresVerification(root))
        assertTrue(AfterEvaluationAction.projectRequiresVerification(sub1))
        assertTrue(AfterEvaluationAction.projectRequiresVerification(subsub1))
        assertFalse(AfterEvaluationAction.projectRequiresVerification(sub2))

        // simulate: gradle jar sub1:subsub1:verifyInstrumentation publish
        gradle.startParameter.setTaskNames(["jar", "sub1:subsub1:verifyInstrumentation", "publish"])
        gradle.startParameter.setCurrentDir(root.getProjectDir())
        assertFalse(AfterEvaluationAction.projectRequiresVerification(root))
        assertFalse(AfterEvaluationAction.projectRequiresVerification(sub1))
        assertTrue(AfterEvaluationAction.projectRequiresVerification(subsub1))
        assertFalse(AfterEvaluationAction.projectRequiresVerification(sub2))

        // simulate: cd sub1/subsub1 && gradle verifyInstrumentation
        gradle.startParameter.setTaskNames(["verifyInstrumentation"])
        gradle.startParameter.setCurrentDir(subsub1.getProjectDir())
        assertFalse(AfterEvaluationAction.projectRequiresVerification(root))
        assertFalse(AfterEvaluationAction.projectRequiresVerification(sub1))
        assertTrue(AfterEvaluationAction.projectRequiresVerification(subsub1))
        assertFalse(AfterEvaluationAction.projectRequiresVerification(sub2))
    }

    @Test
    void testCompileVsVerifyDeps() {
        Project myproject = ProjectBuilder.builder().withName("myproject").build()
        myproject.pluginManager.apply 'java'
        myproject.pluginManager.apply 'com.newrelic.gradle-verify-instrumentation-plugin'
        assertNotNull(myproject.tasks.verifyInstrumentation)
        myproject.gradle.startParameter.setTaskNames(["verifyInstrumentation"])
        myproject.gradle.startParameter.setCurrentDir(myproject.getProjectDir())

        MavenClient.INSTANCE = new MavenClient() {
            @Override
            Collection<String> resolveAvailableVersions(String rangeDep, List<RemoteRepository> repositories) {
                if (rangeDep.startsWith("foo")) {
                    return [
                            'foo:bar:1.0'
                            , 'foo:bar:2.0'
                            , 'foo:bar:2.9'
                    ]
                }
                return []
            }
        }
        myproject.configure(myproject.configurations.compile) {
            compile 'classpathdep:one:1.0'
        }
        myproject.configure((Object)myproject.extensions.verifyInstrumentation) {
            nrAgent = new File('/not/real/newrelic-agent.jar')
            verifyClasspath = true

            passesOnly('foo:bar:[1.0,3.0)') {
                compile 'biz:wiz:3'
            }
        }

        myproject.afterEvaluate() {
            List<Task> passesTasks = getPassesTasks(myproject.getTasks())
            // three from passesOnly and one from the classpath
            assertEquals(
                    new HashSet<>(Arrays.asList("verifyPass_classpath", "verifyPass_foo_bar_1.0", "verifyPass_foo_bar_2.0", "verifyPass_foo_bar_2.9")),
                    passesTasks.stream().map({task -> task.name}).collect(Collectors.toSet())
            )
        }
        myproject.evaluate()
    }

    private static List<Task> getPassesTasks(Set<Task> allTasks) {
        List<Task> passes = []
        for (Task task : allTasks) {
            if (task.getName().startsWith("verifyPass")) {
                passes.add(task)
            }
        }
        return passes
    }
}