/*
 * Copyright 2020 New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.instrumentation.verify;

import org.eclipse.aether.util.version.GenericVersionScheme;
import org.eclipse.aether.version.InvalidVersionSpecificationException;
import org.eclipse.aether.version.Version;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.newrelic.agent.instrumentation.verify.VersionGroupingUtil.GroupingScheme;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies that exclusions are applied to the candidate version list BEFORE grouping selects
 * the highest version per group - so excluding a group's max version falls back to the next
 * highest surviving version in that group, instead of discarding the whole group.
 */
class MavenClientTest {

    private static final GenericVersionScheme SCHEME = new GenericVersionScheme();
    private static final String NAME = "foo:bar";

    private static Version v(String version) {
        try {
            return SCHEME.parseVersion(version);
        } catch (InvalidVersionSpecificationException e) {
            throw new RuntimeException(e);
        }
    }

    private static Collection<Version> versions(String... versionStrings) {
        return Arrays.stream(versionStrings).map(MavenClientTest::v).collect(Collectors.toList());
    }

    private static List<Pattern> excludePatterns(String... regexes) {
        return Arrays.stream(regexes).map(Pattern::compile).collect(Collectors.toList());
    }

    @Test
    void excludedMaxVersionFallsBackToNextHighestInGroup() {
        Collection<String> result = MavenClient.filterAndGroupVersions(NAME,
                versions("2.5.0", "2.5.1", "2.5.2"),
                GroupingScheme.LATEST_MINOR,
                excludePatterns("foo:bar:2\\.5\\.2"));

        assertEquals(Collections.singletonList("foo:bar:2.5.1"), result);
    }

    @Test
    void excludingEveryVersionInAGroupDropsTheGroupEntirely() {
        Collection<String> result = MavenClient.filterAndGroupVersions(NAME,
                versions("2.5.0", "2.5.1", "2.5.2", "3.1.0"),
                GroupingScheme.LATEST_MINOR,
                excludePatterns("foo:bar:2\\.5\\..*"));

        assertEquals(Collections.singletonList("foo:bar:3.1.0"), result);
    }

    @Test
    void noExclusionsBehavesLikePlainGrouping() {
        Collection<String> result = MavenClient.filterAndGroupVersions(NAME,
                versions("2.5.0", "2.5.1", "2.5.2"),
                GroupingScheme.LATEST_MINOR,
                Collections.emptyList());

        assertEquals(Collections.singletonList("foo:bar:2.5.2"), result);
    }

    @Test
    void excludingTheOnlyVersionYieldsEmptyResult() {
        Collection<String> result = MavenClient.filterAndGroupVersions(NAME,
                versions("2.5.2"),
                GroupingScheme.LATEST_MINOR,
                excludePatterns("foo:bar:2\\.5\\.2"));

        assertEquals(Collections.emptyList(), result);
    }

    @Test
    void excludedMaxVersionFallsBackWithLatestMajorScheme() {
        Collection<String> result = MavenClient.filterAndGroupVersions(NAME,
                versions("4.0.0", "4.1.0", "4.13.2"),
                GroupingScheme.LATEST_MAJOR,
                excludePatterns("foo:bar:4\\.13\\.2"));

        assertEquals(Collections.singletonList("foo:bar:4.1.0"), result);
    }
}
