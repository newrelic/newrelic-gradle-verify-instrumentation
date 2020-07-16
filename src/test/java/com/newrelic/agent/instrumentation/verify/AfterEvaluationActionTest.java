package com.newrelic.agent.instrumentation.verify;

import org.eclipse.aether.repository.RemoteRepository;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.UnknownProjectException;
import org.gradle.api.artifacts.Dependency;
import org.gradle.internal.impldep.org.testng.Assert;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.io.File;
import java.util.*;
import java.util.function.Function;

import static com.newrelic.agent.instrumentation.verify.VerificationPlugin.VERIFIER_TASK_NAME;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AfterEvaluationActionTest {

    private AfterEvaluationAction testClass;
    private VerifyInstrumentationOptions mockOptions;
    private Logger mockLogger;
    private File mockDestinationDir;
    private Task mockTask;
    private Project mockProject;
    private Function<Project, List<RemoteRepository>> getRepositoryFunction;

    @BeforeEach
    void before() {
        this.mockOptions = mock(VerifyInstrumentationOptions.class);
        this.mockLogger = mock(Logger.class);
        this.mockDestinationDir=mock(File.class);
        this.mockTask = mock(Task.class);
        this.mockProject = mock(Project.class, RETURNS_DEEP_STUBS);
        this.getRepositoryFunction = project -> Collections.emptyList();
    }

    @Test
    void shouldVerifyProjectWithTaskNameVerifyInstrumentation() {
        String projectPath = "/pathToProjectBuildFile";
        List<String> taskNames = new ArrayList<>();
        taskNames.add(VERIFIER_TASK_NAME);
        when(mockProject.getGradle().getStartParameter().getTaskNames()).thenReturn(taskNames);
        when(mockProject.getGradle().getStartParameter().getCurrentDir().getPath()).thenReturn(projectPath);
        when(mockProject.getProjectDir().getPath()).thenReturn(projectPath);

        assertTrue(AfterEvaluationAction.projectRequiresVerification(mockProject));
    }

    @Test
    void shouldVerifyProjectWithProjectPathBeforeTaskName() {
        String projectPath = "/pathToProject/";
        List<String> taskNames = new ArrayList<>();
        taskNames.add(projectPath+VERIFIER_TASK_NAME);
        when(mockProject.getGradle().getStartParameter().getTaskNames()).thenReturn(taskNames);
        when(mockProject.project(":"+projectPath).getProjectDir().getPath()).thenReturn(projectPath);
        when(mockProject.getProjectDir().getPath()).thenReturn(projectPath);

        assertTrue(AfterEvaluationAction.projectRequiresVerification(mockProject));
    }

    @Test
    void shouldNotVerifyProjectDueToTaskName() {
        List<String> taskNames = new ArrayList<>();
        taskNames.add(VERIFIER_TASK_NAME + "needs to be at end. This won't work.");
        when(mockProject.getGradle().getStartParameter().getTaskNames()).thenReturn(taskNames);

        assertFalse(AfterEvaluationAction.projectRequiresVerification(mockProject));
    }

    @Test
    void shouldNotVerifyProjectWhenNoProjectWithPath() {
        String projectPath = "/noProjectAtPath";
        List<String> taskNames = Arrays.asList(projectPath + VERIFIER_TASK_NAME);

        when(mockProject.getGradle().getStartParameter().getTaskNames()).thenReturn(taskNames);
        when(mockProject.project(":"+projectPath)).thenThrow(new UnknownProjectException("nothing here"));

        assertFalse(AfterEvaluationAction.projectRequiresVerification(mockProject));
    }

    @Test
    void shouldCreateDependencyOnAgentOfFileType() {
        AfterEvaluationAction testClass = new AfterEvaluationAction(mockOptions, mockTask, mockLogger, mockDestinationDir, getRepositoryFunction);
        File mockAgent = new File("/agentForTest");
        Project testProject = ProjectBuilder.builder().build();

        testClass.createProjectDependencyOnAgent(testProject, mockAgent);

        assertEquals(1,
                testProject.getConfigurations().getByName(VERIFIER_TASK_NAME).getDependencies().size());
    }

    @Test
    void shouldCreateDependencyOnAgentOfObjectType() {
        AfterEvaluationAction testClass = new AfterEvaluationAction(mockOptions, mockTask, mockLogger, mockDestinationDir, getRepositoryFunction);
        Dependency mockAgent = mock(Dependency.class);
        Project testProject = ProjectBuilder.builder().build();

        testClass.createProjectDependencyOnAgent(testProject, mockAgent);

        assertEquals(1,
                testProject.getConfigurations().getByName(VERIFIER_TASK_NAME).getDependencies().size());
    }

    @Test
    void shouldBuildExcludedRegexAndVersions() {
        AfterEvaluationAction testClass = new AfterEvaluationAction(mockOptions, mockTask, mockLogger, mockDestinationDir, getRepositoryFunction);
        MavenClient mockMavenClient = mock(MavenClient.class);
        Set<String> excludeRegex = new HashSet<String>();
        excludeRegex.add("testExcludedRegex:.*(RC|SEC|M)[0-9]*$'");
        Set<String> resolvedExcludedVersions = new HashSet<String>();
        Collections.addAll(resolvedExcludedVersions, "test:1.0", "test:2.0", "test:3.0");

        when(mockOptions.excludeRegex()).thenReturn(excludeRegex);
        when(mockOptions.exclude()).thenReturn(resolvedExcludedVersions);
        when(mockMavenClient.resolveAvailableVersions(anyString(), anyList()))
                .thenReturn(resolvedExcludedVersions);

        Set<String> result = testClass.buildExcludedVersions(mockOptions, getRepositoryFunction.apply(mockProject), mockMavenClient);
        //merge sets to produce expected
        resolvedExcludedVersions.addAll(excludeRegex);

        Assert.assertEquals(resolvedExcludedVersions, result);
    }
}