/*
 * Copyright 2020 New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.instrumentation.verify;

import org.gradle.api.GradleException;
import org.gradle.workers.WorkAction;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public abstract class VerifyWorkAction implements WorkAction<VerifyParameters> {
    @Override
    public void execute() {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream printStream = new PrintStream(outputStream);
        boolean didApply;

        try (URLClassLoader agentLoader = new URLClassLoader(new URL[] { getParameters().getAgentJar().toURI().toURL() })) {
            Class<?> weavePackageVerifier = agentLoader.loadClass("com.newrelic.weave.verification.WeavePackageVerifier");

            MethodHandle verifyHandle = MethodHandles.publicLookup().findStatic(
                    weavePackageVerifier,
                    "verify",
                    MethodType.methodType(boolean.class, PrintStream.class, String.class, List.class));

            didApply = (boolean) verifyHandle.invoke(
                    printStream,
                    getParameters().getInstrumentationJar().getAbsolutePath(),
                    getParameters().getClasspathJarsAsList());

        } catch (Throwable e) {
            appendContentToFile(getParameters().getVerifierFailuresFile(), getParameters().getVerifierFailuresContent());

            throw new GradleException("The verifier threw an unexpected exception!", e);
        }

        if (getParameters().shouldSuccessfullyApply() == didApply) {
            appendContentToFile(getParameters().getVerifierPassesFile(), getParameters().getVerifierPassesContent());
        } else {
            appendContentToFile(getParameters().getVerifierFailuresFile(), getParameters().getVerifierFailuresContent());

            throw new GradleException(buildFailureMessage(new String(outputStream.toByteArray())));
        }
    }

    private String buildFailureMessage(String verifierOutput) {
        String message = "Verification FAILED. Instrumentation module " + getParameters().getInstrumentationJar().getName();
        if (getParameters().shouldSuccessfullyApply()) {
            message += " SHOULD HAVE applied to " + getParameters().getOriginalDependency() + " and did not.";
        } else {
            message += " SHOULD NOT HAVE applied to " + getParameters().getOriginalDependency() + " but it did.";
        }

        if (getParameters().getSpecifiedRange() != null) {
            message += " You may need to adjust the range \"" + getParameters().getSpecifiedRange() + "\".";
        }

        if (verifierOutput != null && verifierOutput.length() > 0) {
            message += "\nVerifier output:\n" + verifierOutput;
        }

        return message;
    }

    private void appendContentToFile(File targetFile, String content) {
        try (FileWriter passWriter = new FileWriter(targetFile, true)) {
            passWriter.write(content);
            passWriter.flush();
        } catch (Exception ignored) {
        }
    }

}
