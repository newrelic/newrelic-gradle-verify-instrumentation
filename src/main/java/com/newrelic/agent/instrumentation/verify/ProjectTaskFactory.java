/*
 * Copyright 2020 New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.instrumentation.verify;

import com.google.common.collect.ImmutableMap;
import org.eclipse.aether.repository.RemoteRepository;
import org.gradle.api.GradleException;
import org.gradle.api.GradleScriptException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.UnresolvedDependency;
import org.gradle.api.file.RegularFile;
import org.gradle.api.specs.Specs;
import org.gradle.api.tasks.bundling.Jar;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.gradle.api.Task.TASK_TYPE;

public class ProjectTaskFactory {
    private static final String CLASSPATH_DEP_NAME = "classpath";
    private final Logger logger;
    private final File passesFileDir;

    private Project project;
    private Collection<Pattern> excludeVersions;
    private File passesFile;
    private List<RemoteRepository> mavenRepositories;

    public void setPassesFile(String passesFileName) {
        this.passesFile = passesFileName == null || passesFileName.isEmpty() ? null : project.file(passesFileName);
    }

    @SuppressWarnings("ConstantConditions") // I don't trust that the annotations will actually be respected.
    public ProjectTaskFactory(@Nonnull Project project, @Nonnull Collection<String> excludeVersions, Logger logger, File passesFileDir) {
        if (project == null) {
            throw new NullPointerException("project must not be null");
        }
        if (excludeVersions == null) {
            throw new NullPointerException("excludeVersions must not be null");
        }

        this.logger = logger;
        this.project = project;
        this.excludeVersions = excludeVersions.stream().map(Pattern::compile).collect(Collectors.toList());
        this.passesFileDir = passesFileDir;

        mavenRepositories = MavenProjectUtil.getMavenRepositories(project);
    }

    Stream<? extends Task> buildClasspathTasks() {
        Collection<Dependency> dependencies =
                        project.getConfigurations().getByName("implementation").getAllDependencies();

        return addVerifyTask(CLASSPATH_DEP_NAME, true, dependencies, null);
    }

    /**
     * Constructs verification tasks based on "passes" and "fails".
     *
     * <p>NOTE: There is no checking that you don't have the same version in
     * "passes" and "fails". However, one of the tasks will fail.</p>
     */
    Stream<Task> buildExplicitPassFailTasks(VerifyInstrumentationOptions verifyOptions) {
        return Stream.concat(
                expandMapToTasks(verifyOptions.passes(), true),
                expandMapToTasks(verifyOptions.fails(), false)
        );
    }

    private Stream<VerifyTask> expandMapToTasks(Map<String, Collection<String>> entries, boolean shouldSuccessfullyApply) {
        return entries.entrySet().stream()
                .peek(entry -> logger.info("Resolving range: " + entry.getKey()))
                .flatMap(entry ->
                        MavenClient.INSTANCE.resolveAvailableVersions(entry.getKey(), mavenRepositories).stream()
                                .peek(version -> logger.info("--Resolving: " + version))
                                .flatMap(version -> addVerifyTask(version, shouldSuccessfullyApply, entry.getValue(), entry.getKey())));
    }

    /**
     * Construct verification tasks based on combinations of "passesOnly" and "fails".
     *
     * <p>The highest precedence of task is "fails". Versions called out in "fails" will
     * always expect to fail to apply.</p>
     *
     * <p>The second level of precedence of task is "passesOnly". Versions called out here
     * will expect to apply successfully. Versions that were explicit fails will be logged.
     * NOTE: There must be at least one version that generates a passing task.</p>
     *
     * <p>The lowest level of precedence of task is the implicit fail. Versions in the same group
     * and name as, but not included in, the "passesOnly" will be checked for failure.</p>
     */
    Stream<Task> buildTasksForPassesOnly(VerifyInstrumentationOptions verifyOptions) {
        final Set<String> passOnlyVersions = new HashSet<>();
        final Set<String> explicitFails = new HashSet<>();

        // first build the explicit failures ... these take precedence.
        Stream<VerifyTask> explicitFailStream = expandMapToTasks(verifyOptions.fails(), false);
        Collection<Task> explicitTasks = explicitFailStream
                .peek(task -> explicitFails.add(task.getParameters().getOriginalDependency()))
                .collect(Collectors.toSet());

        // add all the passes. We need to collect() so that the stream runs and we can see if we got results.
        Collection<Task> passOnlyTasks = verifyOptions.passesOnly().entrySet().stream().flatMap(entry ->
                MavenClient.INSTANCE.resolveAvailableVersions(entry.getKey(), mavenRepositories).stream()
                        .filter(version -> {
                            if (explicitFails.contains(version)) {
                                logger.info(
                                        "Spec \"{}\" has a fail rule and a passesOnly rule \"{}\". Fail rules take precedence.",
                                        version, entry.getKey());
                                return false;
                            }

                            logger.info("Resolving: {}", version);
                            return true;
                        })
                        .flatMap(version -> addVerifyTask(version, true, entry.getValue(), entry.getKey()))
                        .peek(task -> passOnlyVersions.add(task.getParameters().getOriginalDependency()))
        ).collect(Collectors.toList());

        if (passOnlyVersions.size() == 0) {
            throw new GradleException("Invalid passesOnly verification - no versions found for " +
                    verifyOptions.passesOnly().keySet());
        }

        explicitTasks.addAll(passOnlyTasks);
        Stream<Task> failTaskStream = buildImplicitFailTasks(passOnlyVersions, explicitFails);

        return Stream.concat(explicitTasks.stream(), failTaskStream);
    }

    private Stream<Task> buildImplicitFailTasks(Set<String> passOnlyVersions, Set<String> explicitFails) {
        // each passesOnly could specify a unique group:name combo. Scala modules are notorious for that.
        // To preserve semantics, we want to make sure we generate implicit fail tasks for all the
        // unique group:name combos.
        Stream<String> uniqueRanges = passOnlyVersions.stream()
                .map(version -> Arrays.asList(version.split(":")).subList(0, 2))
                .map(nameGroupList -> String.join(":", nameGroupList))
                .distinct()
                .map(nameGroup -> nameGroup + ":[0,)");

        return uniqueRanges
                .flatMap(fullRange -> buildImplicitFailTasksForRange(fullRange, passOnlyVersions, explicitFails));
    }

    private Stream<Task> buildImplicitFailTasksForRange(String fullRange, Set<String> passOnlyVersions, Set<String> explicitFails) {
        return MavenClient.INSTANCE.resolveAvailableVersions(fullRange, mavenRepositories).stream()
                .filter(version -> !passOnlyVersions.contains(version) && !explicitFails.contains(version))
                .peek(version -> logger.info("Resolving: {}", version))
                .flatMap(version -> addVerifyTask(version, false, Collections.emptyList(), fullRange));
    }

    private Stream<VerifyTask> addVerifyTask(final String dep, boolean shouldSuccessfullyApply, Collection<?> compileDeps, String specifiedRange) {
        boolean isExcluded = excludeVersions.stream().anyMatch(excludePattern -> excludePattern.matcher(dep).matches());

        if (isExcluded) {
            return Stream.empty();
        }

        final String configName = configName(dep);
        Configuration config = project.getConfigurations().create(configName);

        if (!dep.equals(CLASSPATH_DEP_NAME)) {
            project.getDependencies().add(configName, dep);
        }
        compileDeps.forEach(compileDep -> project.getDependencies().add(configName, compileDep));

        Set<File> configFiles = config.getResolvedConfiguration().getLenientConfiguration().getFiles(Specs.SATISFIES_ALL);
        final Set<UnresolvedDependency> unresolved = config.getResolvedConfiguration().getLenientConfiguration().getUnresolvedModuleDependencies();

        File agentJar = findAgentDependency();
        RegularFile instrumentationJar = findInstrumentationJar();

        VerifyTask task = (VerifyTask) project.task(ImmutableMap.of(TASK_TYPE, VerifyTask.class), taskName(dep, shouldSuccessfullyApply));

        if (!unresolved.isEmpty()) {
            logger.debug(task.getName() + " has unresolved dependencies: " + unresolved);
        }

        // Write the failures to a file for Jenkins automation
        String outputContent = project.getPath().replace(":", "/").substring(1) + " " + dep + "\n";

        VerifyParameters parameters = new VerifyParameters()
                .setOriginalDependency(dep)
                .setSpecifiedRange(specifiedRange)
                .setAgentJar(agentJar)
                .setInstrumentationJar(instrumentationJar.getAsFile())
                .setShouldSuccessfullyApply(shouldSuccessfullyApply)
                .setPrintSuccess(project.hasProperty("printSuccess"))
                .setClasspathJars(configFiles)
                .setVerifierFailures(outputContent, project.file(passesFileDir + "/failures.txt"))
                .setVerifierPasses(outputContent, this.passesFile == null
                        ? project.file(passesFileDir + "/passes.txt")
                        : this.passesFile);

        // Pass the required parameters to the `VerifyTask`
        task.setParameters(parameters);

        return Stream.of(task);
    }

    private File findAgentDependency() {
        Optional<File> agentFileOpt = project.getConfigurations().getByName(VerificationPlugin.VERIFIER_TASK_NAME).getFiles().stream()
                .filter(file -> file.getName().startsWith("newrelic-agent") || file.getName().equalsIgnoreCase("newrelic.jar"))
                .findFirst();

        if (!agentFileOpt.isPresent()) {
            throw new GradleScriptException("newrelic-agent not found; ensure `nrAgent` is set", new Exception());
        }
        return agentFileOpt.get();
    }

    private RegularFile findInstrumentationJar() {
        Jar jarTask = (Jar) project.getTasks().getByName("jar");
        RegularFile instrumentationFile = jarTask.getArchiveFile().getOrNull();

        if (instrumentationFile == null) {
            throw new GradleScriptException("No instrumentation jar available", new Exception());
        }
        return instrumentationFile;
    }

    private String configName(String dep) {
        return "config_" + dep.replaceAll(":", "_");
    }

    private String taskName(String dep, boolean assertPass) {
        return (assertPass ? "verifyPass_" : "verifyFail_") + dep.replaceAll(":", "_");
    }
}
