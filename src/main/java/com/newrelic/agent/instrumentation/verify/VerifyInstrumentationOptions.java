/*
 * Copyright 2020 New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.instrumentation.verify;

import org.gradle.api.Action;
import org.gradle.api.IllegalDependencyNotation;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class VerifyInstrumentationOptions {
    /**
     * Adds a version or a range of versions to the list of versions that will be checked
     * to ensure the instrumentation applies successfully.
     *
     * @param dependencyRangeSpec A range like "group:name:[1.5,3.4)" or a single version.
     */
    public void passes(String dependencyRangeSpec) {
        checkDependencySpec(dependencyRangeSpec);
        passSpecs.put(dependencyRangeSpec, Collections.emptyList());
    }

    /**
     * Adds a version or a range of versions to the list of versions that will be checked
     * to ensure the instrumentation applies successfully, as long as specific compile
     * dependencies (specified in the lambda) are available during instrumentation.
     *
     * @param dependencyRangeSpec A range like "group:name:[1.5,3.4)" or a single version.
     * @param compileSpecAction Used to define compile dependencies for verification.
     */
    public void passes(String dependencyRangeSpec, Action<ImplementationSpec> compileSpecAction) {
        checkDependencySpec(dependencyRangeSpec);
        passSpecs.put(dependencyRangeSpec, getCompileDependencies(compileSpecAction));
    }

    /**
     * Gets the set of version specs, and corresponding compile dependencies,
     * that will be checked to ensure the instrumentation applies successfully.
     *
     * @return the set of version specs and compile dependencies for passes
     */
    public Map<String, Collection<String>> passes() {
        return passSpecs;
    }

    /**
     * Adds a version or a range of versions to the list of versions that will be checked
     * to both ensure that instrumentation is applied successfully, AND that all versions
     * outside the final list fail to apply successfully.
     *
     * @param dependencyRangeSpec A range like "group:name:[1.5,3.4)" or a single version.
     */
    public void passesOnly(String dependencyRangeSpec) {
        checkDependencySpec(dependencyRangeSpec);
        passOnlySpecs.put(dependencyRangeSpec, Collections.emptyList());
    }

    /**
     * Adds a version or a range of versions to the list of versions that will be checked
     * to both ensure that instrumentation is applied successfully, AND that all versions
     * outside the final list fail to apply successfully.
     *
     * @param dependencyRangeSpec A range like "group:name:[1.5,3.4)" or a single version.
     * @param compileSpecAction Used to define compile dependencies for verification.
     */
    public void passesOnly(String dependencyRangeSpec, Action<ImplementationSpec> compileSpecAction) {
        checkDependencySpec(dependencyRangeSpec);
        passOnlySpecs.put(dependencyRangeSpec, getCompileDependencies(compileSpecAction));
    }

    /**
     * Gets the versions for which instrumentation should apply successfully. The complement
     * of this set of versions will be checked to ensure that instrumentation fail to apply
     * successfully.
     *
     * @return the set of version specs and compile dependencies for passesOnly
     */
    public Map<String, Collection<String>> passesOnly() {
        return passOnlySpecs;
    }

    /**
     * Adds a version or a range of versions to the list of versions that will be checked
     * to ensure the instrumentation does not apply successfully.
     *
     * @param dependencyRangeSpec A range like "group:name:[1.5,3.4)" or a single version.
     */
    public void fails(String dependencyRangeSpec) {
        checkDependencySpec(dependencyRangeSpec);
        failSpecs.put(dependencyRangeSpec, Collections.emptyList());
    }

    /**
     * Adds a version or a range of versions to the list of versions that will be checked
     * to ensure the instrumentation does not apply successfully.
     *
     * @param dependencyRangeSpec A range like "group:name:[1.5,3.4)" or a single version.
     * @param compileSpecAction Used to define compile dependencies for verification.
     */
    public void fails(String dependencyRangeSpec, Action<ImplementationSpec> compileSpecAction) {
        checkDependencySpec(dependencyRangeSpec);
        failSpecs.put(dependencyRangeSpec, getCompileDependencies(compileSpecAction));
    }

    /**
     * Gets the set of version ranges that will be checked to ensure the instrumentation
     * does not apply successfully.
     *
     * @return the set of version specs and compile dependencies for fails
     */
    public Map<String, Collection<String>> fails() {
        return failSpecs;
    }

    /**
     * Adds a single dependency that should be excluded completely - it will not be checked
     * for success or failure to apply. Ranges are supported
     *
     * @param specToExclude A range like "group:name:[1.5,3.4)" or a single version.
     */
    public void exclude(String specToExclude) {
        excludes.add(specToExclude);
    }

    /**
     * Gets the dependencies that should not be checked for either success or failure to apply.
     */
    Set<String> exclude() {
        return excludes;
    }

    /**
     * Adds a regular expression where any version that matches it will not be checked for success
     * or failure to apply.
     *
     * <p>
     *     The typical use for this parameter is to exclude SNAPSHOT, beta, RC releases,
     *     using a regex like "group:name:.*beta.*",
     * </p>
     *
     * @param regexToExclude The regex that should be excluded.
     */
    public void excludeRegex(String regexToExclude) {
        excludeRegexes.add(regexToExclude);
    }

    /**
     * Gets the regular expressions that should block matching versions from being checked for
     * success or failure to apply.
     */
    Set<String> excludeRegex() {
        return excludeRegexes;
    }

    /**
     * Contains an optional path for a consolidated pass/fail list.
     */
    public String passesFileName = "";

    /**
     * True if the instrumentation should also be checked (in addition to pass/fail) as valid using only
     * the dependencies called out in "compile" and "implementation" jar dependencies.
     */
    public boolean verifyClasspath = false;

    /**
     * Sets the New Relic Java Agent fat jar location; this helps ensure that the application
     * of instrumentation uses the appropriate version of the code.
     *
     * @param value The file location for the New Relic Java Agent fat jar.
     */
    public void setNrAgent(String value) {
        nrAgent = value;
    }
    /**
     * Sets the New Relic Java Agent fat jar location; this helps ensure that the application
     * of instrumentation uses the appropriate version of the code.
     *
     * @param value The {@link File} location for the New Relic Java Agent fat jar.
     */
    public void setNrAgent(File value) {
        nrAgent = value;
    }

    Object getNrAgent() {
        return nrAgent;
    }

    private void checkDependencySpec(String dependencySpec) {
        if (!dependencySpec.matches("^[^:]+:[^:]+:[^:]+$")) {
            throw new IllegalDependencyNotation("The dependency must be \"group:name:versions\", but got \"" + dependencySpec + "\"");
        }
    }

    private Object nrAgent = "com.newrelic.agent.java:newrelic-agent:+";

    private Collection<String> getCompileDependencies(Action<ImplementationSpec> compileSpecAction) {
        ImplementationSpec spec = new ImplementationSpec();
        compileSpecAction.execute(spec);
        return spec.getImplementationDependencies();
    }

    private final Map<String, Collection<String>> passSpecs = new HashMap<>();
    private final Map<String, Collection<String>> passOnlySpecs = new HashMap<>();
    private final Map<String, Collection<String>> failSpecs = new HashMap<>();
    private final Set<String> excludes = new HashSet<>();
    private final Set<String> excludeRegexes = new HashSet<>();
}

