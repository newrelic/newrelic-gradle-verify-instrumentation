/*
 * Copyright 2020 New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.instrumentation.verify;

import org.eclipse.aether.repository.RemoteRepository;
import org.gradle.api.Project;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;

import java.util.ArrayList;
import java.util.List;

class MavenProjectUtil {
    /**
     * Return a list of maven RemoteRepositories from the user's build.gradle sources.
     */
    static List<RemoteRepository> getMavenRepositories(Project project) {
        List<RemoteRepository> mavenRepositories = new ArrayList<>(project.getRepositories().size());
        for (ArtifactRepository repo : project.getRepositories()) {
            if (repo instanceof MavenArtifactRepository) {
                MavenArtifactRepository mavenRepo = (MavenArtifactRepository) repo;
                project.getLogger().info("Using maven repo to fetch verifier sources: " + mavenRepo.getUrl());
                RemoteRepository remoteRepository = new RemoteRepository.Builder(
                        mavenRepositories.size() + "",
                        "default",
                        mavenRepo.getUrl().toString())
                        .build();
                mavenRepositories.add(remoteRepository);
            } else {
                project.getLogger().warn("Only maven repos are supported. Unable to fetch sources from: " + repo);
            }
        }
        return mavenRepositories;
    }

    private MavenProjectUtil() {}
}
