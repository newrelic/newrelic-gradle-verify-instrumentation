package com.newrelic.agent.instrumentation.verify;

import org.eclipse.aether.repository.RemoteRepository;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.UnknownProjectException;
import org.gradle.api.artifacts.Dependency;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;

import java.io.File;
import java.util.*;
import java.util.function.Function;

import static com.newrelic.agent.instrumentation.verify.VerificationPlugin.VERIFIER_TASK_NAME;
import static org.gradle.internal.impldep.org.testng.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class AfterEvaluationActionTest {

    private AfterEvaluationAction testClass;
    private Function<Project, List<RemoteRepository>> getRepositoryFunction;

    @BeforeEach
    void beforeEach() {
        MockitoAnnotations.initMocks(this);
        this.getRepositoryFunction = project -> Collections.emptyList();
        this.testClass = new AfterEvaluationAction(mockVerifyOptions, mockVerifyInstrumentationTask, mockLogger, mockPassesFileDir, getRepositoryFunction);
    }

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    public Project mockProject;

    @Mock
    public Task mockVerifyInstrumentationTask;

    @Mock
    public File mockPassesFileDir;

    @Mock
    public Logger mockLogger;

    @Mock
    public VerifyInstrumentationOptions mockVerifyOptions;


    @Test
    void shouldVerifyProjectWithTaskNameVerifyInstrumentation() {
        String projectPath = "/pathToProjectBuildFile";
        List<String> taskNames = new ArrayList<>();
        taskNames.add(VERIFIER_TASK_NAME);
        when(mockProject.getGradle().getStartParameter().getTaskNames()).thenReturn(taskNames);
        when(mockProject.getGradle().getStartParameter().getCurrentDir().getPath()).thenReturn(projectPath);
        when(mockProject.getProjectDir().getPath()).thenReturn(projectPath);

        assertTrue(testClass.projectRequiresVerification(mockProject));
    }

    @Test
    void shouldVerifyProjectWithProjectPathBeforeTaskName() {
        String projectPath = "/pathToProject/";
        List<String> taskNames = new ArrayList<>();
        taskNames.add(projectPath+VERIFIER_TASK_NAME);
        when(mockProject.getGradle().getStartParameter().getTaskNames()).thenReturn(taskNames);
        when(mockProject.project(":"+projectPath).getProjectDir().getPath()).thenReturn(projectPath);
        when(mockProject.getProjectDir().getPath()).thenReturn(projectPath);

        assertTrue(testClass.projectRequiresVerification(mockProject));
    }

    @Test
    void shouldNotVerifyProjectDueToTaskName() {
        List<String> taskNames = new ArrayList<>();
        taskNames.add(VERIFIER_TASK_NAME + "needs to be at end. This won't work.");
        when(mockProject.getGradle().getStartParameter().getTaskNames()).thenReturn(taskNames);

        assertFalse(testClass.projectRequiresVerification(mockProject));
    }

    @Test
    void shouldNotVerifyProjectWhenNoProjectWithPath() {
        String projectPath = "/noProjectAtPath";
        List<String> taskNames = Arrays.asList(projectPath + VERIFIER_TASK_NAME);

        when(mockProject.getGradle().getStartParameter().getTaskNames()).thenReturn(taskNames);
        when(mockProject.project(":"+projectPath)).thenThrow(new UnknownProjectException("nothing here"));

        assertFalse(testClass.projectRequiresVerification(mockProject));
    }

    @Test
    void shouldCreateDependencyOnAgentOfFileType() {
        File mockAgent = new File("/agentForTest");
        Project testProject = ProjectBuilder.builder().build();

        testClass.createProjectDependencyOnAgent(testProject, mockAgent);

        assertEquals(1,
                testProject.getConfigurations().getByName(VERIFIER_TASK_NAME).getDependencies().size());
    }

    @Test
    void shouldCreateDependencyOnAgentOfObjectType() {
        Dependency mockAgent = mock(Dependency.class);
        Project testProject = ProjectBuilder.builder().build();

        testClass.createProjectDependencyOnAgent(testProject, mockAgent);

        assertEquals(1,
                testProject.getConfigurations().getByName(VERIFIER_TASK_NAME).getDependencies().size());
    }

    @Test
    void shouldBuildExcludedRegexAndVersions() {
        MavenClient mockMavenClient = mock(MavenClient.class);
        Set<String> excludeRegex = new HashSet<String>();
        excludeRegex.add("testExcludedRegex:.*(RC|SEC|M)[0-9]*$'");
        Set<String> resolvedExcludedVersions = new HashSet<String>();
        Collections.addAll(resolvedExcludedVersions, "test:1.0", "test:2.0", "test:3.0");

        when(mockVerifyOptions.excludeRegex()).thenReturn(excludeRegex);
        when(mockVerifyOptions.exclude()).thenReturn(resolvedExcludedVersions);
        when(mockMavenClient.resolveAvailableVersions(anyString(), anyList()))
                .thenReturn(resolvedExcludedVersions);

        Set<String> result = testClass.buildExcludedVersions(mockVerifyOptions, getRepositoryFunction.apply(mockProject), mockMavenClient);
        //merge sets to produce expected
        resolvedExcludedVersions.addAll(excludeRegex);

        assertEquals(resolvedExcludedVersions, result);
    }
}