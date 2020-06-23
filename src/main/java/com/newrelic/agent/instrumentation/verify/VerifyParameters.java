/*
 * Copyright 2020 New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.instrumentation.verify;

import org.gradle.workers.WorkParameters;

import java.io.File;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@SuppressWarnings("UnstableApiUsage")
public class VerifyParameters implements WorkParameters, Serializable {

    private String taskName;
    private File instrumentationJar;
    private boolean shouldSuccessfullyApply;
    private String verifierFailuresContent;
    private File verifierFailuresFile;
    private String verifierPassesContent;
    private File verifierPassesFile;
    private boolean printSuccess;
    private File agentJar;
    private Set<File> classpathJars;
    private String originalDependency;
    private String specifiedRange;

    public String getTaskName() {
        return taskName;
    }

    public VerifyParameters setTaskName(String taskName) {
        this.taskName = taskName;
        return this;
    }

    public File getInstrumentationJar() {
        return instrumentationJar;
    }

    public VerifyParameters setInstrumentationJar(File instrumentationJar) {
        this.instrumentationJar = instrumentationJar;
        return this;
    }

    public boolean shouldSuccessfullyApply() {
        return shouldSuccessfullyApply;
    }

    public VerifyParameters setShouldSuccessfullyApply(boolean shouldSuccessfullyApply) {
        this.shouldSuccessfullyApply = shouldSuccessfullyApply;
        return this;
    }

    public String getVerifierFailuresContent() {
        return verifierFailuresContent;
    }

    public File getVerifierFailuresFile() {
        return verifierFailuresFile;
    }

    public String getVerifierPassesContent() {
        return verifierPassesContent;
    }

    public File getVerifierPassesFile() {
        return verifierPassesFile;
    }

    public VerifyParameters setVerifierFailures(String verifierFailuresContent, File verifierFailuresFile) {
        this.verifierFailuresContent = verifierFailuresContent;
        this.verifierFailuresFile = verifierFailuresFile;
        return this;
    }

    public VerifyParameters setVerifierPasses(String verifierPassesContent, File verifierPassesFile) {
        this.verifierPassesContent = verifierPassesContent;
        this.verifierPassesFile = verifierPassesFile;
        return this;
    }

    public boolean isPrintSuccess() {
        return printSuccess;
    }

    public VerifyParameters setPrintSuccess(boolean printSuccess) {
        this.printSuccess = printSuccess;
        return this;
    }

    public File getAgentJar() {
        return agentJar;
    }

    public VerifyParameters setAgentJar(File agentJar) {
        this.agentJar = agentJar;
        return this;
    }

    public Set<File> getClasspathJars() {
        return classpathJars;
    }

    public List<String> getClasspathJarsAsList() {
        if (classpathJars == null || classpathJars.isEmpty()) {
            return Collections.emptyList();
        }

        return classpathJars.stream()
                .filter(Objects::nonNull)
                .map(File::getAbsolutePath)
                .collect(Collectors.toList());
    }

    public VerifyParameters setClasspathJars(Set<File> classpathJars) {
        this.classpathJars = classpathJars;
        return this;
    }

    public String getOriginalDependency() {
        return originalDependency;
    }

    public VerifyParameters setOriginalDependency(String originalDependency) {
        this.originalDependency = originalDependency;
        return this;
    }

    public String getSpecifiedRange() {
        return specifiedRange;
    }

    public VerifyParameters setSpecifiedRange(String specifiedRange) {
        this.specifiedRange = specifiedRange;
        return this;
    }

    public VerifyParameters setFrom(VerifyParameters parameters) {
        return this.setAgentJar(parameters.getAgentJar())
                .setShouldSuccessfullyApply(parameters.shouldSuccessfullyApply())
                .setClasspathJars(parameters.getClasspathJars())
                .setInstrumentationJar(parameters.getInstrumentationJar())
                .setTaskName(parameters.getTaskName())
                .setOriginalDependency(parameters.getOriginalDependency())
                .setPrintSuccess(parameters.isPrintSuccess())
                .setSpecifiedRange(parameters.getSpecifiedRange())
                .setVerifierFailures(parameters.getVerifierFailuresContent(), parameters.getVerifierFailuresFile())
                .setVerifierPasses(parameters.getVerifierPassesContent(), parameters.getVerifierPassesFile());
    }

    private static final long serialVersionUID = 3L;
}
