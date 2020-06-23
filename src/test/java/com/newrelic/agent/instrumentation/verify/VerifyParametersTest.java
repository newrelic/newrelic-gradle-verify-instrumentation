/*
 * Copyright 2020 New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.instrumentation.verify;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VerifyParametersTest {
    @Test
    public void serializeRoundTrip() throws Exception {
        VerifyParameters target = new VerifyParameters()
                .setAgentJar(new File("/tmp/foo"))
                .setShouldSuccessfullyApply(true)
                .setClasspathJars(new HashSet<>(Arrays.asList(new File("/tmp/foo2"), new File("/tmp/foo3"))))
                .setInstrumentationJar(new File("/tmp/foo4"))
                .setOriginalDependency("original dependency");

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
        objectOutputStream.writeObject(target);

        byte[] result = byteArrayOutputStream.toByteArray();
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(result);
        ObjectInputStream objectInputStream = new ObjectInputStream(byteArrayInputStream);

        Object deserializedObj = objectInputStream.readObject();
        assertTrue(deserializedObj instanceof VerifyParameters, "deserialized object was not VerifyParameters, was " + deserializedObj.getClass());
        VerifyParameters deserialized = (VerifyParameters) deserializedObj;

        assertEquals(target.getAgentJar(), deserialized.getAgentJar());
        assertEquals(target.shouldSuccessfullyApply(), deserialized.shouldSuccessfullyApply());
        assertEquals(target.getInstrumentationJar(), deserialized.getInstrumentationJar());
        assertEquals(target.getOriginalDependency(), deserialized.getOriginalDependency());
    }
}
