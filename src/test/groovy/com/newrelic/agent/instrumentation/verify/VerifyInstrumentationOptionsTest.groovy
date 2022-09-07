/*
 * Copyright 2020 New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.instrumentation.verify

import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals


class VerifyInstrumentationOptionsTest {
    @Test
    void testPasses() {
        VerifyInstrumentationOptions verifyInstrumentation = new VerifyInstrumentationOptions()
        verifyInstrumentation.passes('foo:foo:1.0') {
            it.implementation('fooDep1:foo:1.0')
            it.implementation'fooDep2:foo:1.0'
        }
        verifyInstrumentation.passes("bar:foo:1.0")
        verifyInstrumentation.passes "baz:foo:1.0"

        assertEquals(3, verifyInstrumentation.passes().size())
        assertEquals(2, verifyInstrumentation.passes().get('foo:foo:1.0').size())
        assertEquals(0, verifyInstrumentation.passes().get('bar:foo:1.0').size())
        assertEquals(0, verifyInstrumentation.passes().get('baz:foo:1.0').size())
        assertEquals('fooDep1:foo:1.0', verifyInstrumentation.passes().get('foo:foo:1.0')[0])
        assertEquals('fooDep2:foo:1.0', verifyInstrumentation.passes().get('foo:foo:1.0')[1])
    }

    @Test
    void testFails() {
        VerifyInstrumentationOptions verifyInstrumentation = new VerifyInstrumentationOptions()
        verifyInstrumentation.fails('foo:foo:1.0') {
            it.implementation('fooDep1:foo:1.0')
            it.implementation 'fooDep2:foo:1.0'
        }
        verifyInstrumentation.fails('bar:foo:1.0')
        verifyInstrumentation.fails 'baz:foo:1.0'

        assertEquals(3, verifyInstrumentation.fails().size())
        assertEquals(2, verifyInstrumentation.fails().get('foo:foo:1.0').size())
        assertEquals(0, verifyInstrumentation.fails().get('bar:foo:1.0').size())
        assertEquals(0, verifyInstrumentation.fails().get('baz:foo:1.0').size())
        assertEquals('fooDep1:foo:1.0', verifyInstrumentation.fails().get('foo:foo:1.0')[0])
        assertEquals('fooDep2:foo:1.0', verifyInstrumentation.fails().get('foo:foo:1.0')[1])
    }

    @Test
    void testPassesOnly() {
        VerifyInstrumentationOptions verifyInstrumentation = new VerifyInstrumentationOptions()
        verifyInstrumentation.passesOnly('foo:foo:1.0') {
            it.implementation('fooDep1:foo:1.0')
            it.implementation 'fooDep2:foo:1.0'
        }
        verifyInstrumentation.passesOnly('bar:foo:1.0')
        verifyInstrumentation.passesOnly 'baz:foo:1.0'

        assertEquals(3, verifyInstrumentation.passesOnly().size())
        assertEquals(2, verifyInstrumentation.passesOnly().get('foo:foo:1.0').size())
        assertEquals(0, verifyInstrumentation.passesOnly().get('bar:foo:1.0').size())
        assertEquals(0, verifyInstrumentation.passesOnly().get('baz:foo:1.0').size())
        assertEquals('fooDep1:foo:1.0', verifyInstrumentation.passesOnly().get('foo:foo:1.0')[0])
        assertEquals('fooDep2:foo:1.0', verifyInstrumentation.passesOnly().get('foo:foo:1.0')[1])
    }
}