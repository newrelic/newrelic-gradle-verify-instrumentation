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
    void excludingMaxVersionFallsBackToNextHighest() {
        Collection<Version> result = MavenClient.excludeAndGroupVersions(NAME,
                versions("2.5.0", "2.5.1", "2.5.2", "2.6.0-rc"),
                GroupingScheme.LATEST_MAJOR,
                excludePatterns("(?i).*2.6.*-RC.*"));

        assertEquals(Collections.singletonList("foo:bar:2.5.2"), completeVersionNames(result, NAME));
    }

    @Test
    void excludingEveryVersionEliminatesGroup() {
        Collection<Version> result = MavenClient.excludeAndGroupVersions(NAME,
                versions("2.5.0", "2.5.1", "2.5.2", "3.1.0"),
                GroupingScheme.LATEST_MINOR,
                excludePatterns("foo:bar:2\\.5\\..*"));

        assertEquals(Collections.singletonList("foo:bar:3.1.0"), completeVersionNames(result, NAME));
    }

    @Test
    void noExclusionsGroupsNormally() {
        Collection<Version> result = MavenClient.excludeAndGroupVersions(NAME,
                versions("2.5.0", "2.5.1", "2.5.2"),
                GroupingScheme.LATEST_MINOR,
                Collections.emptyList());

        assertEquals(Collections.singletonList("foo:bar:2.5.2"), completeVersionNames(result, NAME));

        result = MavenClient.excludeAndGroupVersions(NAME,
                versions("2.5.0", "2.5.1", "2.5.2"),
                GroupingScheme.ALL,
                Collections.emptyList());

        assertEquals(Arrays.asList("foo:bar:2.5.0", "foo:bar:2.5.1", "foo:bar:2.5.2"), completeVersionNames(result, NAME));
    }

    private Collection<String> completeVersionNames(Collection<Version> versions, String artifact) {
        return versions.stream().map(version -> artifact + ":" + version.toString()).collect(Collectors.toList());
    }
}
