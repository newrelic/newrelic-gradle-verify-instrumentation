/*
 * Copyright 2020 New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.instrumentation.verify;

import java.util.ArrayList;
import java.util.List;

public class ImplementationSpec {
    public void implementation(String dependencySpec) {
        ImplementationDependencies.add(dependencySpec);
    }

    List<String> getImplementationDependencies() {
        return ImplementationDependencies;
    }

    private List<String> ImplementationDependencies = new ArrayList<>();
}
