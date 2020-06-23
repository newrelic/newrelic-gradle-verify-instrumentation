/*
 * Copyright 2020 New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.instrumentation.verify;

import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.eclipse.aether.version.Version;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class MavenClient {
    public static MavenClient INSTANCE = new MavenClient();

    public Collection<String> resolveAvailableVersions(String rangeDep, List<RemoteRepository> repositories) {
        Collection<Version> versions;
        try {
            versions = getVersions(rangeDep, repositories);
        } catch (VersionRangeResolutionException ignored) {
            return Collections.emptyList();
        }

        String[] parts = rangeDep.split(":");
        final String name = parts[0] + ":" + parts[1];

        return versions.stream().map(version -> name + ":" + version.toString()).collect(Collectors.toList());
    }

    private static Collection<Version> getVersions(String artifactName, List<RemoteRepository> repositories) throws VersionRangeResolutionException {
        RepositorySystem system = newRepositorySystem();

        RepositorySystemSession session = newRepositorySystemSession(system);
        Artifact artifact = new DefaultArtifact(artifactName);

        VersionRangeRequest rangeRequest = new VersionRangeRequest()
                .setArtifact(artifact)
                .setRepositories(repositories);

        VersionRangeResult rangeResult = system.resolveVersionRange(session, rangeRequest);

        return rangeResult.getVersions();
    }

    private static RepositorySystem newRepositorySystem() {
        DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator()
                .addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class)
                .addService(TransporterFactory.class, FileTransporterFactory.class)
                .addService(TransporterFactory.class, HttpTransporterFactory.class);

        locator.setErrorHandler(new DefaultServiceLocator.ErrorHandler() {
            @Override
            public void serviceCreationFailed(Class<?> type, Class<?> impl, Throwable exception) {
                exception.printStackTrace();
            }
        });

        return locator.getService(RepositorySystem.class);
    }

    private static DefaultRepositorySystemSession newRepositorySystemSession(RepositorySystem system) {
        LocalRepository localRepo = new LocalRepository("/tmp/target/local-repo");

        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();

        LocalRepositoryManager localRepositoryManager = system.newLocalRepositoryManager(session, localRepo);

        return session.setLocalRepositoryManager(localRepositoryManager);
    }
}
