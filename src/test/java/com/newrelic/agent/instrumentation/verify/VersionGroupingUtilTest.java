package com.newrelic.agent.instrumentation.verify;

import org.eclipse.aether.util.version.GenericVersionScheme;
import org.eclipse.aether.version.InvalidVersionSpecificationException;
import org.eclipse.aether.version.Version;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static com.newrelic.agent.instrumentation.verify.VersionGroupingUtil.*;

/**
 * Tests document the INTENDED behavior of {@link VersionGroupingUtil#groupVersions(Collection, GroupingScheme)}:
 * versions are grouped by Major.Minor, and within each group only the highest version is kept.
 * Versions that cannot be parsed into at least Major.Minor are always included in the result.
 */
class VersionGroupingUtilTest {

    private static final GenericVersionScheme SCHEME = new GenericVersionScheme();

    private static Version v(String version) {
        try {
            return SCHEME.parseVersion(version);
        } catch (InvalidVersionSpecificationException e) {
            throw new RuntimeException(e);
        }
    }

    private static Collection<Version> versions(String... versionStrings) {
        return Arrays.stream(versionStrings).map(VersionGroupingUtilTest::v).collect(Collectors.toList());
    }

    private static List<String> asStrings(Collection<Version> versions) {
        return versions.stream().map(Version::toString).sorted().collect(Collectors.toList());
    }

    @Test
    void returnsEmptyCollectionForEmptyInput() {
        Collection<Version> result = VersionGroupingUtil.groupVersions(versions(), GroupingScheme.LATEST_MINOR);
        assertTrue(result.isEmpty());
    }

    @Test
    void singleVersionIsReturnedAsIsForAllSchemes() {
        Collection<Version> result = VersionGroupingUtil.groupVersions(versions("4.13.1"), GroupingScheme.LATEST_MINOR);
        assertEquals(Collections.singletonList("4.13.1"), asStrings(result));

        result = VersionGroupingUtil.groupVersions(versions("4.13.1"), GroupingScheme.LATEST_MAJOR);
        assertEquals(Collections.singletonList("4.13.1"), asStrings(result));

        result = VersionGroupingUtil.groupVersions(versions("4.13.1"), GroupingScheme.LATEST);
        assertEquals(Collections.singletonList("4.13.1"), asStrings(result));

        result = VersionGroupingUtil.groupVersions(versions("4.13.1"), GroupingScheme.ALL);
        assertEquals(Collections.singletonList("4.13.1"), asStrings(result));
    }

    @Test
    void testGroupingByAllKeepsEverything() {
        Collection<Version> result = VersionGroupingUtil.groupVersions(
                versions("4.13.0", "4.13.1", "4.13.2"),  GroupingScheme.ALL);
        assertEquals(Arrays.asList("4.13.0", "4.13.1", "4.13.2"), asStrings(result));

        result = VersionGroupingUtil.groupVersions(
                versions("3.0.0", "3.0.5", "3.1.0", "4.0.0", "4.0.1", "4.1.0", "4.1.9"), GroupingScheme.ALL);
        assertEquals(Arrays.asList("3.0.0", "3.0.5", "3.1.0", "4.0.0", "4.0.1", "4.1.0", "4.1.9"), asStrings(result));

        result = VersionGroupingUtil.groupVersions(
                versions("4.13.1.100-RELEASE", "4.13.1.200-RELEASE",
                        "4.13.1.200-BETA", "2.5.0-rc", "2.5.0-beta", "0.1.0"), GroupingScheme.ALL);
        assertEquals(Arrays.asList("0.1.0", "2.5.0-beta", "2.5.0-rc",
                "4.13.1.100-RELEASE", "4.13.1.200-BETA", "4.13.1.200-RELEASE"), asStrings(result));
    }

    @Test
    void testGroupingByLatestKeepsOnlyOne() {
        Collection<Version> result = VersionGroupingUtil.groupVersions(
                versions("4.13.0", "4.13.1", "4.13.2"),  GroupingScheme.LATEST);
        assertEquals(Collections.singletonList("4.13.2"), asStrings(result));

        result = VersionGroupingUtil.groupVersions(
                versions("3.0.0", "3.0.5", "3.1.0", "4.0.0", "4.0.1", "4.1.0", "4.1.9"), GroupingScheme.LATEST);
        assertEquals(Collections.singletonList("4.1.9"), asStrings(result));

        result = VersionGroupingUtil.groupVersions(
                versions("4.13.1.100-RELEASE", "4.13.1.200-RELEASE",
                        "4.13.1.200-BETA", "2.5.0-rc", "2.5.0-beta", "0.1.0"), GroupingScheme.LATEST);
        assertEquals(Collections.singletonList("4.13.1.200-RELEASE"), asStrings(result));
    }

    @Test
    void testGroupingByMinorVersion() {
        //All same minor versions should be condensed to the single highest version.
        Collection<Version> result = VersionGroupingUtil.groupVersions(
                versions("4.13.0", "4.13.1", "4.13.2"),  GroupingScheme.LATEST_MINOR);
        assertEquals(Collections.singletonList("4.13.2"), asStrings(result));

        // 4.13.1 and 5.13.0 share the same minor version ("13"),
        // but belong to different Major.Minor groups and must be kept separate.
        result = VersionGroupingUtil.groupVersions(
                versions("4.13.1", "5.13.0"), GroupingScheme.LATEST_MINOR);
        assertEquals(Arrays.asList("4.13.1", "5.13.0"), asStrings(result));

        // 5.0.0 should not be dropped in favor of 4.99.0, even though 99 > 0,
        // because they belong to different Major.Minor groups.
        result = VersionGroupingUtil.groupVersions(
                versions("4.99.0", "5.0.0"), GroupingScheme.LATEST_MINOR);
        assertEquals(Arrays.asList("4.99.0", "5.0.0"), asStrings(result));

        //If for some reason there are duplicate entries, only one should be kept.
        result = VersionGroupingUtil.groupVersions(
                versions("4.13.1", "4.13.1"), GroupingScheme.LATEST_MINOR);
        assertEquals(Collections.singletonList("4.13.1"), asStrings(result));

        //Below models the typical case: multiple different major and minor versions present.
        result = VersionGroupingUtil.groupVersions(
                versions("3.0.0", "3.0.5", "3.1.0", "4.0.0", "4.0.1", "4.1.0", "4.1.9"), GroupingScheme.LATEST_MINOR);
        assertEquals(Arrays.asList("3.0.5", "3.1.0", "4.0.1", "4.1.9"), asStrings(result));
    }

    @Test
    void groupingByMinorVersionKeepsVersionsWithoutMinorComponent() {
        // A version like "5" has no minor component and is included by default,
        // regardless of what other versions are present.
        Collection<Version> result = VersionGroupingUtil.groupVersions(
                versions("5", "4.13.0", "4.13.1", "5.1"), GroupingScheme.LATEST_MINOR);
        assertEquals(Arrays.asList("4.13.1", "5", "5.1"), asStrings(result));
    }

    @Test
    void groupingByMinorVersionWorksForExtendedVersioning() {
        //Incremental builds should be grouped into the same minor version.
        Collection<Version> result = VersionGroupingUtil.groupVersions(
                versions("4.13.1.100", "4.13.1.200"), GroupingScheme.LATEST_MINOR);
        assertEquals(Collections.singletonList("4.13.1.200"), asStrings(result));

        // GenericVersion sorts releases above snapshots.
        result = VersionGroupingUtil.groupVersions(
                versions("4.13.1-SNAPSHOT", "4.13.1"), GroupingScheme.LATEST_MINOR);
        assertEquals(Collections.singletonList("4.13.1"), asStrings(result));

        //The full scheme is Major.Minor.Incremental.BuildNumber-Qualifier. Check that those work as expected.
        result = VersionGroupingUtil.groupVersions(
                versions("4.13.1.100-RELEASE", "4.13.1.200-RELEASE", "4.13.1.200-BETA"), GroupingScheme.LATEST_MINOR);
        assertEquals(Collections.singletonList("4.13.1.200-RELEASE"), asStrings(result));

        // GenericVersions sorts (alpha < beta < rc) even without any build number present.
        result = VersionGroupingUtil.groupVersions(
                versions("2.5.0-alpha", "2.5.0-beta", "2.5.0-rc"), GroupingScheme.LATEST_MINOR);
        assertEquals(Collections.singletonList("2.5.0-rc"), asStrings(result));

        //The qualifier may include "." tokens as well.
        result = VersionGroupingUtil.groupVersions(
                versions("1.5.0-beta.1", "1.5.0-beta.2", "1.5.0"), GroupingScheme.LATEST_MINOR);
        assertEquals(Collections.singletonList("1.5.0"), asStrings(result));
    }

    @Test
    void testGroupingByMajorVersion() {
        //All same major versions should be condensed to the single highest version.
        Collection<Version> result = VersionGroupingUtil.groupVersions(
                versions("4.13.0", "4.13.1", "4.13.2", "4.0.6", "4.1.0"),  GroupingScheme.LATEST_MAJOR);
        assertEquals(Collections.singletonList("4.13.2"), asStrings(result));

        //different major versions are kept separate.
        result = VersionGroupingUtil.groupVersions(
                versions("4.13.1", "5.13.0"), GroupingScheme.LATEST_MAJOR);
        assertEquals(Arrays.asList("4.13.1", "5.13.0"), asStrings(result));


        //If for some reason there are duplicate entries, only one should be kept.
        result = VersionGroupingUtil.groupVersions(
                versions("4.13.1", "4.13.1"), GroupingScheme.LATEST_MAJOR);
        assertEquals(Collections.singletonList("4.13.1"), asStrings(result));

        //Below models the typical case: multiple different major and minor versions present.
        result = VersionGroupingUtil.groupVersions(
                versions("3.0.0", "3.0.5", "3.1.0", "4.0.0", "4.0.1", "4.1.0", "4.1.9"), GroupingScheme.LATEST_MAJOR);
        assertEquals(Arrays.asList("3.1.0", "4.1.9"), asStrings(result));
    }

    @Test
    void groupingByMajorVersionWorksForExtendedVersioning() {
        //Incremental builds should be grouped by major version.
        Collection<Version> result = VersionGroupingUtil.groupVersions(
                versions("4.13.1.100", "4.13.1.200", "4.12.28.4", "4.0", "5.3.26-33"), GroupingScheme.LATEST_MAJOR);
        assertEquals(Arrays.asList("4.13.1.200", "5.3.26-33"), asStrings(result));

        // GenericVersion sorts releases above snapshots.
        result = VersionGroupingUtil.groupVersions(
                versions("4.13.1-SNAPSHOT", "4.13.1"), GroupingScheme.LATEST_MAJOR);
        assertEquals(Collections.singletonList("4.13.1"), asStrings(result));

        //The full scheme is Major.Minor.Incremental.BuildNumber-Qualifier. Check that those work as expected.
        result = VersionGroupingUtil.groupVersions(
                versions("4.13.1.100-RELEASE", "4.13.1.200-RELEASE", "4.13.1.200-BETA", "4.11.3.0-BETA"), GroupingScheme.LATEST_MAJOR);
        assertEquals(Collections.singletonList("4.13.1.200-RELEASE"), asStrings(result));

        // GenericVersions sorts (alpha < beta < rc) even without any build number present.
        result = VersionGroupingUtil.groupVersions(
                versions("2.5.0-alpha", "2.5.0-beta", "2.5.0-rc"), GroupingScheme.LATEST_MAJOR);
        assertEquals(Collections.singletonList("2.5.0-rc"), asStrings(result));

        //The qualifier may include "." tokens as well.
        result = VersionGroupingUtil.groupVersions(
                versions("1.5.0-beta.1", "1.5.0-beta.2", "1.5.0", "1.2.0"), GroupingScheme.LATEST_MAJOR);
        assertEquals(Collections.singletonList("1.5.0"), asStrings(result));
    }

}
