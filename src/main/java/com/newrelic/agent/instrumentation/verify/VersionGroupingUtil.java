package com.newrelic.agent.instrumentation.verify;

import org.eclipse.aether.version.Version;
import org.gradle.api.GradleException;

import java.util.List;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import java.util.Collections;
import java.util.stream.Collectors;

/**
 * Utility class to extract only some versions from a version list, according to a requested grouping scheme.
 * <p>
 * The resolved lists of versions is often extremely large, especially for artifacts that have frequent (daily)
 * releases. To mitigate this, the plugin supports a `versionGrouping` parameter. This parameter groups an artifact's
 * versions according to the scheme, and then selects only the highest artifact version among each group.
 * <p>
 * Grouping schemes (ordered by specificity) are ALL, LATEST_MINOR, LATEST_MAJOR, LATEST.
 * <p>
 * For example: given the list of artifact versions [2.5.1, 2.5.2, 2.6.0, 3.1.12, 3.1.33, 4.1.7]:
 *   * ALL selects [2.5.1, 2.5.2, 2.6.0, 3.1.12, 3.1.33, 4.1.7]
 *   * LATEST_MINOR selects [2.5.2, 2.6.0, 3.1.33, 4.1.7]
 *   * LATEST_MAJOR selects [2.6.0, 3.1.33, 4.1.7]
 *   * LATEST selects [4.1.7]
 * <p>
 * This property can be set with the `versionGrouping` option on the command line or in the configured build.gradle
 * task, e.g.: `-PversionGrouping=LATEST_MINOR`. If not specified in either place, the default is ALL. 
 */
public class VersionGroupingUtil {

    public enum GroupingScheme {
        ALL,
        LATEST_MINOR,
        LATEST_MAJOR,
        LATEST;
    }

    private static final String VERSION_PART_DELIMITER = "[.-]";

    public static GroupingScheme parse(String value) {
        try {
            return GroupingScheme.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new GradleException("Invalid value \"" + value + "\" for property 'versionGrouping'. Must be one of " + Arrays.toString(GroupingScheme.values()));
        }
    }

    /**
     * Groups {@code versions} according to {@code groupingScheme} and keeps only the highest version
     * in each group.
     *
     * @return the filtered versions, sorted in ascending order for convenience.
     */
    public static List<Version> groupVersions(Collection<Version> versions, GroupingScheme groupingScheme) {
        if (groupingScheme == GroupingScheme.ALL) {
            List<Version> versionList = new ArrayList<>(versions);
            Collections.sort(versionList);
            return versionList;
        }
        if (groupingScheme == GroupingScheme.LATEST) {
            return Collections.singletonList(Collections.max(versions));
        }

        //Group the versions according to Version's implementation of Comparable, and aggregate them by max version.
        Map<String, Optional<Version>> latestVersionByGroup = versions.stream()
                .collect(Collectors.groupingBy(version ->
                        getVersionGroup(version, groupingScheme), Collectors.maxBy(Comparator.naturalOrder())
                ));

        return latestVersionByGroup.values().stream()
                .map(Optional::get)
                .sorted()
                .collect(Collectors.toList());
    }

    private static String getVersionGroup(Version version, GroupingScheme groupingScheme) {
        switch (groupingScheme) {
            case LATEST_MAJOR : return getMajorVersion(version);
            case LATEST_MINOR : return getMinorVersion(version);
            default : return version.toString();
        }
    }

    private static String getMinorVersion(Version version) {
        String[] versionComponents = version.toString().split(VERSION_PART_DELIMITER);
        if (versionComponents.length < 2) {
            //if for any reason the version cannot be parsed, return the complete version string.
            //this will sort the string into its own bucket (so it will be included by default).
            return version.toString();
        }
        return String.join(".", versionComponents[0], versionComponents[1]);
    }

    private static String getMajorVersion(Version version) {
        String[] versionComponents = version.toString().split(VERSION_PART_DELIMITER);
        if (versionComponents.length < 1) {
            //if the major version could not be parsed, return the complete version string.
            //this will sort the string into its own bucket (so it will be included by default).
            return version.toString();
        }
        return versionComponents[0];
    }

}
