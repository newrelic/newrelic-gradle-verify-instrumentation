/*
 * Copyright 2020 New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.instrumentation.verify;

import java.util.ArrayList;
import java.util.List;

public class CompileSpec {
    public void compile(String dependencySpec) {
        compileDependencies.add(dependencySpec);
    }

    List<String> getCompileDependencies() {
        return compileDependencies;
    }

    private List<String> compileDependencies = new ArrayList<>();
}
